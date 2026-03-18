package app.morphe.extension.shared.requests;

public class Route {
    private final String route;
    private final Method method;
    private final int paramCount;

    public Route(Method method, String route) {
        this.method = method;
        this.route = route;
        this.paramCount = countMatches(route, '{');

        if (paramCount != countMatches(route, '}'))
            throw new IllegalArgumentException("Not enough parameters");
    }

    public Method getMethod() {
        return method;
    }

    public CompiledRoute compile(String... params) {
        if (params.length != paramCount)
            throw new IllegalArgumentException("Error compiling route [" + route + "], incorrect amount of parameters provided. " +
                    "Expected: " + paramCount + ", provided: " + params.length);

        StringBuilder compiledRoute = new StringBuilder(route);
        for (int i = 0; i < paramCount; i++) {
            int paramStart = compiledRoute.indexOf("{");
            int paramEnd = compiledRoute.indexOf("}");
            compiledRoute.replace(paramStart, paramEnd + 1, params[i]);
        }
        return new CompiledRoute(this, compiledRoute.toString());
    }

    public static class CompiledRoute {
        private final Route baseRoute;
        private final String compiledRoute;

        private CompiledRoute(Route baseRoute, String compiledRoute) {
            this.baseRoute = baseRoute;
            this.compiledRoute = compiledRoute;
        }

        public String getCompiledRoute() {
            return compiledRoute;
        }

        public Method getMethod() {
            return baseRoute.method;
        }
    }

    private int countMatches(CharSequence seq, char c) {
        int count = 0;
        for (int i = 0, length = seq.length(); i < length; i++) {
            if (seq.charAt(i) == c)
                count++;
        }
        return count;
    }

    public enum Method {
        GET,
        POST
    }
}