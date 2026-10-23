#!/bin/sh
# Probe for the NewX inline-download CDN failures (video.twimg.com UnknownHostException).
#
# Run in Termux:  ./dns-probe.sh [rounds] [sleep_seconds]
# Example:        ./dns-probe.sh 12 10    # 12 rounds, ~2 minutes
#
# Each round resolves every host (DNS) and fetches the host root over HTTPS,
# then prints per-host stats: success/fail counts, failure %, avg HTTP time.

ROUNDS=${1:-12}
SLEEP_SECS=${2:-10}
TIMEOUT=10

HOSTS="video.twimg.com pbs.twimg.com api.x.com"

have() { command -v "$1" >/dev/null 2>&1; }
tag() { printf '%s' "$1" | tr '.' '_'; }
now() { date '+%H:%M:%S'; }

echo "=== NewX CDN probe ==="
echo "rounds=$ROUNDS sleep=${SLEEP_SECS}s timeout=${TIMEOUT}s started=$(now)"
echo "--- device DNS config ---"
if have getprop; then
    echo "net.dns1=[$(getprop net.dns1 2>/dev/null)]"
    echo "net.dns2=[$(getprop net.dns2 2>/dev/null)]"
else
    echo "getprop: MISSING"
fi
if have settings; then
    echo "private_dns_mode=[$(settings get global private_dns_mode 2>/dev/null || echo unreadable)]"
else
    echo "settings: MISSING"
fi
if [ -r /etc/resolv.conf ]; then
    echo "resolv.conf: $(grep -v '^#' /etc/resolv.conf 2>/dev/null | grep nameserver | tr '\n' ' ')"
fi
if have nslookup; then
    DNS_TOOL="nslookup"
elif have ping; then
    DNS_TOOL="ping"
else
    DNS_TOOL="none"
fi
if have curl; then echo "dns_tool=$DNS_TOOL curl=yes"; else echo "dns_tool=$DNS_TOOL curl=no"; fi
echo ""

for h in $HOSTS; do
    t=$(tag "$h")
    eval "dns_ok_$t=0; dns_fail_$t=0; http_ok_$t=0; http_fail_$t=0; http_time_sum_$t=0; http_time_n_$t=0"
done

probe_dns() {
    case $DNS_TOOL in
        nslookup) nslookup "$1" >/dev/null 2>&1 ;;
        ping) ping -c1 -W3 "$1" >/dev/null 2>&1 ;;
        none) return 2 ;;
    esac
}

probe_http() {
    # prints "<code> <seconds>"; exit 0 with code 000 on any curl failure
    curl -o /dev/null -s -w "%{http_code} %{time_total}" \
        --max-time "$TIMEOUT" "https://$1/" 2>/dev/null || printf '000 0'
}

round=0
while [ "$round" -lt "$ROUNDS" ]; do
    round=$((round + 1))
    line="round $round/$ROUNDS [$(now)]"
    for h in $HOSTS; do
        t=$(tag "$h")
        probe_dns "$h"
        dns_rc=$?
        if [ "$dns_rc" -eq 0 ]; then
            dns_res="OK"; eval "dns_ok_$t=$((dns_ok_$t + 1))"
        elif [ "$dns_rc" -eq 2 ]; then
            dns_res="SKIP"
        else
            dns_res="FAIL"; eval "dns_fail_$t=$((dns_fail_$t + 1))"
        fi
        if have curl; then
            out=$(probe_http "$h")
            code=$(printf '%s' "$out" | awk '{print $1}')
            secs=$(printf '%s' "$out" | awk '{print $2}')
            case $code in
                000) http_res="FAIL"; eval "http_fail_$t=$((http_fail_$t + 1))" ;;
                *) http_res="$code"; eval "http_ok_$t=$((http_ok_$t + 1))"
                   eval "prev_sum=\$http_time_sum_$t; prev_n=\$http_time_n_$t"
                   new_sum=$(awk "BEGIN {print $prev_sum + $secs}")
                   eval "http_time_sum_$t=$new_sum; http_time_n_$t=$((prev_n + 1))" ;;
            esac
            line="$line | $h dns=$dns_res http=$http_res t=${secs}s"
        else
            line="$line | $h dns=$dns_res http=SKIP"
        fi
    done
    echo "$line"
    if [ "$round" -lt "$ROUNDS" ]; then
        sleep "$SLEEP_SECS"
    fi
done

echo ""
echo "=== STATS ($ROUNDS rounds) ==="
for h in $HOSTS; do
    t=$(tag "$h")
    eval "d_ok=\$dns_ok_$t; d_fail=\$dns_fail_$t; h_ok=\$http_ok_$t; h_fail=\$http_fail_$t"
    eval "t_sum=\$http_time_sum_$t; t_n=\$http_time_n_$t"
    d_tot=$((d_ok + d_fail))
    h_tot=$((h_ok + h_fail))
    if [ "$d_tot" -gt 0 ]; then
        d_pct=$(awk "BEGIN {printf \"%.0f\", 100*$d_fail/$d_tot}")
        dns_stat="dns: $d_ok ok / $d_fail FAIL (${d_pct}% failed)"
    else
        dns_stat="dns: no data (no tool)"
    fi
    if [ "$h_tot" -gt 0 ]; then
        h_pct=$(awk "BEGIN {printf \"%.0f\", 100*$h_fail/$h_tot}")
        if [ "$t_n" -gt 0 ]; then
            avg=$(awk "BEGIN {printf \"%.2f\", $t_sum/$t_n}")
        else
            avg="n/a"
        fi
        http_stat="http: $h_ok ok / $h_fail FAIL (${h_pct}% failed), avg ${avg}s"
    else
        http_stat="http: no data (no curl)"
    fi
    echo "$h"
    echo "  $dns_stat"
    echo "  $http_stat"
done
echo "finished=$(now)"
