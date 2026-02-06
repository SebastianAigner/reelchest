import {useEffect, useRef} from "react";
import useSWR, {SWRConfiguration, SWRResponse} from "swr";
import useSWRImmutable from "swr/immutable";

type AbortableFetcher<Data> = (url: string, init?: RequestInit) => Promise<Data>;

type SWRHook = <Data, ErrorType>(
    key: string | null,
    fetcher: ((key: string) => Promise<Data>) | null,
    config?: SWRConfiguration<Data, ErrorType>
) => SWRResponse<Data, ErrorType>;

function useAbortableSWRInternal<Data, ErrorType>(
    swrHook: SWRHook,
    key: string | null,
    fetcher: AbortableFetcher<Data>,
    config?: SWRConfiguration<Data, ErrorType>
): SWRResponse<Data, ErrorType> {
    const abortRef = useRef<AbortController | null>(null);

    const swrResponse = swrHook<Data, ErrorType>(
        key,
        key
            ? async (url: string) => {
                abortRef.current?.abort();
                const controller = new AbortController();
                abortRef.current = controller;
                try {
                    return await fetcher(url, {signal: controller.signal});
                } finally {
                    if (abortRef.current === controller) {
                        abortRef.current = null;
                    }
                }
            }
            : null,
        config
    );

    useEffect(() => {
        if (!key) {
            abortRef.current?.abort();
            abortRef.current = null;
        }
        return () => {
            abortRef.current?.abort();
        };
    }, [key]);

    const abortableError = swrResponse.error as {name?: string} | undefined;
    if (abortableError?.name === "AbortError") {
        return {...swrResponse, error: undefined};
    }

    return swrResponse;
}

export function useAbortableSWR<Data, ErrorType = any>(
    key: string | null,
    fetcher: AbortableFetcher<Data>,
    config?: SWRConfiguration<Data, ErrorType>
): SWRResponse<Data, ErrorType> {
    return useAbortableSWRInternal<Data, ErrorType>(useSWR, key, fetcher, config);
}

export function useAbortableSWRImmutable<Data, ErrorType = any>(
    key: string | null,
    fetcher: AbortableFetcher<Data>,
    config?: SWRConfiguration<Data, ErrorType>
): SWRResponse<Data, ErrorType> {
    return useAbortableSWRInternal<Data, ErrorType>(useSWRImmutable, key, fetcher, config);
}
