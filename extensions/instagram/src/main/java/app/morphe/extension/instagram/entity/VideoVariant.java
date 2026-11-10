/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.entity;

import java.util.Collections;
import java.util.Map;

/** Quality information supplied by Instagram; absent values do not imply lower quality. */
public interface VideoVariant extends MediaInterface {
    default String getVideoCodec() {
        return "";
    }

    default int getBitrate() {
        return 0;
    }

    /** Playback resolution to MOS, so only scores measured at the same size are compared. */
    default Map<Integer, Double> getQualityScores() {
        return Collections.emptyMap();
    }
}
