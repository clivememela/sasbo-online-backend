import { useState, useEffect } from 'react';

// This hook delays updating a value until a specified delay time passes
export function useDebounce(value, delay = 400) {
    const [debouncedValue, setDebouncedValue] = useState(value);

    useEffect(() => {
        // Set a timer to update the value after the delay
        const handler = setTimeout(() => {
            setDebouncedValue(value);
        }, delay);

        // If the value changes again before the timer ends, cancel the old timer
        return () => {
            clearTimeout(handler);
        };
    }, [value, delay]);

    return debouncedValue;
}