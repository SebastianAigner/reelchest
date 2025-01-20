import {Link, useLocation, useParams} from "react-router-dom";
import * as React from "react";
import {useState} from "react";
import {MediaLibraryEntry} from "../models/MediaLibraryEntry";
import {MainHeading} from "../components/Typography";
import axios from "axios";
import useSWR from "swr/esm";
import {fetcher} from "../utils";
import {mutate} from "swr";
import EdiText from "react-editext";
import {INPUT_ACTION, SimpleInputField} from "../components/SimpleInputField";
import {AutoTaggedMediaLibraryEntry} from "../models/AutoTaggedMediaLibraryEntry";
import * as _ from "underscore";
import DuplicatesDTO = IoSebi.DuplicatesDTO;

interface Identifiable {
    id: string
}

// type Partial<T> = {
//     [P in keyof T]?: T[P];
// }

function useStoredDuplicates(id) {
    const endpoint = `/api/mediaLibrary/${id}/duplicates`
    const {data, error} = useSWR<DuplicatesDTO>(endpoint, fetcher)
    return {
        entry: data,
        isLoading: !error && !data,
        isError: error,
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

let throttledApiEvent = _.throttle((id, time) => {
    console.log("running throttled function!")
    axios.post("/api/event", {
        id: id,
        timestamp: time
    })
}, 1000)

export function Movie() {
    const {id} = useParams<Identifiable>();
    const location = useLocation();
    const queryParams = new URLSearchParams(location.search);
    const showPlayer = queryParams.get("showPlayer") != "false";
    const [clicked, setClicked] = useState(false);
    const {entry, isLoading, isError, mutateEntry} = useMediaLibraryEntry(id)
    const {data, error} = useSWR<MediaLibraryEntry>(`/api/mediaLibrary/${id}/possibleDuplicates`, fetcher)
    const {entry: storedDuplicates, isError: storedDuplicatesError} = useStoredDuplicates(id)
    if (!entry) return <>
        <p>loading...</p>
    </>
    return <>
        <MainHeading><EdiText value={entry.mediaLibraryEntry.name} type={"text"} onSave={(value) => {
            mutateEntry({name: value})
        }}/></MainHeading>
        {showPlayer && (
            <video src={`/api/video/${id}`} width={"500px"} controls={true} onPlay={() => {
                if (!clicked) {
                    setClicked(true)
                    axios.get(`/api/mediaLibrary/${id}/hit`)
                }
            }} onTimeUpdate={(event) => {
                let time = event.currentTarget.currentTime;
                console.log(`updating time! ${time}`);
                throttledApiEvent(id, time);
            }}/>
        )}

        <h3 className={"text-2xl"}>Operations</h3>
        <ul>
            <li>
                <button onClick={() =>
                    mutateEntry({markedForDeletion: !entry?.mediaLibraryEntry.markedForDeletion})
                }>{entry?.mediaLibraryEntry.markedForDeletion ? "Unmark " : "Mark "} for deletion
                </button>
            </li>
            <li><a href={`vlc://${window.location.host}/api/video/${id}`}>Open in VLC</a></li>
            <li><a href={`/api/video/${id}.mp4`}>Direct link</a></li>
            <li><a href={`/api/file/${entry?.mediaLibraryEntry.name}`}>Direct link (by name)</a></li>
        </ul>
        <h3 className={"text-2xl"}>Tags</h3>
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
        <h3 className={"text-2xl"}>Autotags</h3>
        {entry.autoTags.map((tag) => {
            return <li key={tag}>
                <p className={"bg-green-500 text-white font-semibold rounded-lg shadow-md focus:outline-none p-2 inline-block"}>
                    <Link to={`/mediaLibrary/${tag}`}>{tag}</Link>
                </p>
            </li>
        })}
        <h3 className={"text-2xl"}>Duplicate detection</h3>
        {data &&
            <>
                <p>{data.id}</p>
                <p>{data.name}</p>
                <p><Link to={`/movie/${data.id}`}>Link here.</Link></p>
            </>
        }
        <h3 className={"text-2xl"}>Persisted duplicate detection</h3>
        {
            storedDuplicates &&
            <>
                <p>{storedDuplicates.dup_id}</p>
                <p>{storedDuplicates.distance}</p>
            </>
        }
    </>
}
