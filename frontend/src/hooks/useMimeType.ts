import useSWRImmutable from "swr/immutable";

const fetcher = (url: string) => fetch(url).then(r => r.json());

export function useMimeType(id: string) {
    const endpoint = `/api/mediaLibrary/${id}/mime-type`;
    const {data, error} = useSWRImmutable<{ mimeType: string }>(endpoint, fetcher);
    return {
        mimeType: data?.mimeType,
        isAudio: data?.mimeType?.startsWith('audio/'),
        isLoading: !error && !data,
        isError: error
    };
}
