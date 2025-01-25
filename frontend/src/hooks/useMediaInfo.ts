import useSWRImmutable from "swr/immutable";

const fetcher = (url: string) => fetch(url).then(r => r.json());

interface MediaInfo {
    mimeType: string;
    width?: number;
    height?: number;
}

export function useMediaInfo(id: string) {
    const endpoint = `/api/mediaLibrary/${id}/media-information`;
    const {data, error} = useSWRImmutable<MediaInfo>(endpoint, fetcher);
    return {
        mimeType: data?.mimeType,
        width: data?.width,
        height: data?.height,
        isAudio: data?.mimeType?.startsWith('audio/'),
        isLoading: !error && !data,
        isError: error
    };
}