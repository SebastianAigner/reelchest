import * as React from "react";
import useSWR from "swr";
import {fetcher} from "../utils";
import {MainHeading, SectionHeading} from "../components/Typography";
import {commonStyles} from "../styles/common";
import {AutoTaggedMediaLibraryEntry} from "../models/AutoTaggedMediaLibraryEntry";

function useMediaLibraryEntries() {
    const {data, error} = useSWR<Array<AutoTaggedMediaLibraryEntry>>(
        "/api/mediaLibrary?auto=true",
        fetcher
    );

    return {
        entries: data || [],
        isLoading: !data && !error,
        isError: error
    };
}

export function Admin() {
    const {entries, isLoading, isError} = useMediaLibraryEntries();
    const markedForDeletionCount = React.useMemo(() => {
        return entries.filter(entry => entry.mediaLibraryEntry.markedForDeletion).length;
    }, [entries]);

    return <>
        <MainHeading>Admin</MainHeading>
        <SectionHeading>Maintenance</SectionHeading>
        <div className={`${commonStyles.cardContainer} ${commonStyles.standardMargin}`}>
            <div className="text-sm uppercase tracking-wide text-gray-500 dark:text-gray-400">
                Files marked for deletion
            </div>
            <div className="text-4xl font-semibold mt-2">
                {isLoading ? "Loading..." : markedForDeletionCount}
            </div>
            {isError && (
                <p className={`${commonStyles.errorText} mt-3`}>
                    Failed to load deletion status.
                </p>
            )}
        </div>
    </>;
}
