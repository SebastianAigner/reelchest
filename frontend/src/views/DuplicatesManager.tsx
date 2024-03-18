import * as React from "react";
import useSWR from "swr/esm";
import {fetcher} from "../utils";
import {MediaLibraryEntry} from "../models/MediaLibraryEntry";
import {MediaLibraryCard} from "../components/MediaLibraryCard";
import axios from "axios";
import {mutate} from "swr";
import {AutoTaggedMediaLibraryEntry} from "../models/AutoTaggedMediaLibraryEntry";
import DuplicateResponse = IoSebi.DuplicateResponse;

function useDupData() {
    const {data, error} = useSWR<Array<DuplicateResponse>>(`/api/mediaLibrary/duplicates`, fetcher)
    return {
        dupRes: data,
        isLoading: !error && !data,
        isError: error
    }
}

function useMediaLibraryEntry(id) {
    const endpoint = `/api/mediaLibrary/${id}?auto=true`
    const {data, error} = useSWR<AutoTaggedMediaLibraryEntry>(endpoint, fetcher)
    return {
        entry: data,
        isLoading: !error && !data,
        isError: error,
        mutateEntry: (newEntry: Partial<MediaLibraryEntry>) => {
            if (!data?.mediaLibraryEntry) return
            const joinedEntry = {...data.mediaLibraryEntry, ...newEntry} as MediaLibraryEntry
            console.log(data.mediaLibraryEntry)
            console.log(joinedEntry)
            axios.post(`/api/mediaLibrary/${id}`, joinedEntry).then(() => {
                console.log("post succeeded, mutating!")
                mutate(endpoint)
            })
        }
    }
}

function DuplicateRow(props: DuplicateResponse) {
    const {entry, isLoading, isError, mutateEntry} = useMediaLibraryEntry(props.possibleDuplicate.id)
    return <>
        <>
            <div>
                <p>Distance: {props.distance}</p>
                <button onClick={() =>
                    mutateEntry({markedForDeletion: !entry?.mediaLibraryEntry.markedForDeletion})
                }>{entry?.mediaLibraryEntry.markedForDeletion ? "Unmark " : "Mark "} for deletion
                </button>
            </div>

            <MediaLibraryCard item={props.entry}/>
            <MediaLibraryCard item={props.possibleDuplicate}/>
        </>
    </>
}

export function DuplicatesManager() {
    const {dupRes, isLoading, isError} = useDupData()
    return <>
        <h2 className={"text-5xl"}>Duplicates</h2>
        <div className={"grid grid-cols-1 sm:grid-cols-3 gap-8"}>
            {
                dupRes?.map(item =>
                    <DuplicateRow {...item}/>
                )
            }
        </div>
    </>;
}