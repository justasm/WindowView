package com.jmedeisis.windowview.sensor;

/**
 * A discrete-time filter for raw sensor values.
 */
public interface Filter {
    /**
     * Update filter with the latest value.
     * @return latest filtered value.
     */
    float push(float value);

    /**
     * Reset filter to the given value.
     */
    void reset(float value);

    /**
     * @return latest filtered value.
     */
    float get();
}
