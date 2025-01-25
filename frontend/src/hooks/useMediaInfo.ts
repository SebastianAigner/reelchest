import useSWRImmutable from "swr/immutable";

const fetcher = (url: string) => {
    const request = new Request(url, {
        priority: 'low' // or 'low' or 'auto'
    });

    return fetch(request).then(r => r.json());
};

interface MediaInfo {
    mimeType: string;
    width?: number;
    height?: number;
}

export function useMediaInfo(id: string, enabled: boolean = true) {
    const endpoint = `/api/mediaLibrary/${id}/media-information`;
    const {data, error} = useSWRImmutable<MediaInfo>(enabled ? endpoint : null, fetcher);
    return {
        mimeType: data?.mimeType,
        width: data?.width,
        height: data?.height,
        isAudio: data?.mimeType?.startsWith('audio/'),
        isLoading: !error && !data,
        isError: error
    };
}
