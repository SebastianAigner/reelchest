import {useAbortableSWRImmutable} from "./useAbortableSWR";

const fetcher = async (url: string, init?: RequestInit) => {
    const request = new Request(url, {
        priority: 'low',
        ...init
    });

    const response = await fetch(request);
    return response.json();
};

interface MediaInfo {
    mimeType: string;
    width?: number;
    height?: number;
}

export function useMediaInfo(id: string, enabled: boolean = true) {
    const endpoint = `/api/mediaLibrary/${id}/media-information`;
    const {data, error} = useAbortableSWRImmutable<MediaInfo>(
        enabled ? endpoint : null,
        fetcher
    );

    return {
        mimeType: data?.mimeType,
        width: data?.width,
        height: data?.height,
        isAudio: data?.mimeType?.startsWith('audio/'),
        isLoading: enabled && !error && !data,
        isError: error
    };
}
