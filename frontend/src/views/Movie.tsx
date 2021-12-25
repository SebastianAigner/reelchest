import {Link, useParams} from "react-router-dom";
import * as React from "react";
import {useState} from "react";
import {MediaLibraryEntry} from "../models/MediaLibraryEntry";
import axios from "axios";
import useSWR from "swr/esm";
import {fetcher} from "../utils";
import {mutate} from "swr";
import EdiText from "react-editext";
import {INPUT_ACTION, SimpleInputField} from "../components/SimpleInputField";
import {AutoTaggedMediaLibraryEntry} from "../models/AutoTaggedMediaLibraryEntry";

interface Identifiable {
    id: string
}

// type Partial<T> = {
//     [P in keyof T]?: T[P];
// }

function useMediaLibraryEntry(id) {
    const endpoint = `/api/mediaLibrary/${id}?auto`
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

export function Movie() {
    const {id} = useParams<Identifiable>();
    const [clicked, setClicked] = useState(false);
    const {entry, isLoading, isError, mutateEntry} = useMediaLibraryEntry(id)
    const {data, error} = useSWR<MediaLibraryEntry>(`/api/mediaLibrary/${id}/possibleDuplicates`, fetcher)
    if (!entry) return <>
        <p>loading...</p>
    </>
    return <>
        <h2 className={"text-5xl"}><EdiText value={entry.mediaLibraryEntry.name} type={"text"} onSave={(value) => {
            mutateEntry({name: value})
        }}/></h2>
        <video src={`/api/video/${id}`} width={"500px"} controls={true} onPlay={() => {
            if (!clicked) {
                setClicked(true)
                axios.get(`/api/mediaLibrary/${id}/hit`)
            }
        }
        }/>
        <button onClick={() =>
            mutateEntry({markedForDeletion: !entry?.mediaLibraryEntry.markedForDeletion})
        }>{entry?.mediaLibraryEntry.markedForDeletion ? "Unmark " : "Mark "} for deletion
        </button>
        <h3>Tags</h3>
        <ul>
            {entry.mediaLibraryEntry.tags.map((tag) => {
                    return <li key={tag}>
                        <p className={"bg-green-500 text-white font-semibold rounded-lg shadow-md focus:outline-none p-2 inline-block"}>
                            {tag}
                            <span onClick={() => {
                                mutateEntry({
                                    tags: entry?.mediaLibraryEntry.tags.filter(item => {
                                        return item != tag
                                    })
                                })
                            }}> x</span>
                        </p>
                    </li>
                }
            )}
        </ul>
        <p>
            <SimpleInputField onSubmit={(value) => {
                mutateEntry({
                    tags: [...entry?.mediaLibraryEntry.tags, value]
                })
                return INPUT_ACTION.CLEAR
            }}/>
        </p>
        <h3>Autotags</h3>
        {entry.autoTags.map((tag) => {
            return <li key={tag}>
                <p className={"bg-green-500 text-white font-semibold rounded-lg shadow-md focus:outline-none p-2 inline-block"}>
                    <Link to={`/mediaLibrary/${tag}`}>{tag}</Link>
                </p>
            </li>
        })}
        <h3>Possible duplicate</h3>
        {data &&
            <>
                <p>{data.id}</p>
                <p>{data.name}</p>
                <p><Link to={`/movie/${data.id}`}>Link here.</Link></p>
            </>
        }
    </>
}