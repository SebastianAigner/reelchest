import {Link, useHistory, useLocation, useParams} from "react-router-dom";
import * as React from "react";
import {useState} from "react";
import {MediaLibraryEntry} from "../models/MediaLibraryEntry";
import {MainHeading, SubHeading} from "../components/Typography";
import {TagBadge} from "../components/TagBadge";
import {usePlaylist} from "../context/PlaylistContext";
import {useMediaInfo} from "../hooks/useMediaInfo";
import axios from "axios";
import {commonStyles} from "../styles/common";
import {StyledButton} from "../components/StyledButton";
import useSWR, {mutate} from "swr";
import {fetcher} from "../utils";
import EdiText from "react-editext";
import {INPUT_ACTION, SimpleInputField} from "../components/SimpleInputField";
import {AutoTaggedMediaLibraryEntry} from "../models/AutoTaggedMediaLibraryEntry";
import * as _ from "underscore";
import {MediaLibraryCard} from "../components/MediaLibraryCard";
import DuplicatesDTO = IoSebi.DuplicatesDTO;

function MediaInfoSection({id}: { id: string }) {
    const {mimeType, width, height, isLoading} = useMediaInfo(id);

    if (isLoading) return <p>Loading media information...</p>;

    return (
        <ul>
            <li>MIME Type: {mimeType}</li>
            {width && height && (
                <li>Dimensions: {width}×{height}</li>
            )}
        </ul>
    );
}

interface Identifiable {
    id: string
}

// type Partial<T> = {
//     [P in keyof T]?: T[P];
// }

function useStoredDuplicate(id: string) {
    const endpoint = `/api/mediaLibrary/${id}/storedDuplicate`
    const {data, error} = useSWR<DuplicatesDTO>(endpoint, fetcher)
    return {
        duplicate: data,
        isLoading: !error && !data,
        isError: error,
    }
}

function useMediaLibraryEntry(id: string) {
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

let throttledApiEvent = _.throttle((id: string, time: number) => {
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
    const {entry, isLoading, isError, mutateEntry} = useMediaLibraryEntry(id);
    const {getNextVideo, getCurrentVideo, peekNextVideo} = usePlaylist();
    const history = useHistory();
    const {duplicate, isLoading: isDuplicateLoading, isError: isDuplicateError} = useStoredDuplicate(id)
    const {entry: duplicateEntry, isLoading: isDuplicateEntryLoading} = useMediaLibraryEntry(duplicate?.dup_id || id)
    // Only use duplicateEntry if duplicate exists and IDs match
    const validDuplicateEntry = duplicate && duplicateEntry && duplicate.dup_id === duplicateEntry.mediaLibraryEntry.id ? duplicateEntry : null
    if (!entry) return <>
        <p>loading...</p>
    </>
    return <>
        <MainHeading><EdiText value={entry.mediaLibraryEntry.name} type={"text"} onSave={(value) => {
            mutateEntry({name: value})
        }}/></MainHeading>
        {showPlayer && (
            <video
                src={`/api/video/${id}`}
                width={"500px"}
                controls={true}
                onEnded={(e) => {
                    e.preventDefault();
                    console.log("[DEBUG_LOG] Video ended, attempting to play next video");
                    const video = e.target as HTMLVideoElement;
                    video.pause();
                    const nextVideo = getNextVideo();
                    if (nextVideo) {
                        console.log("[DEBUG_LOG] Found next video, navigating to:", nextVideo.mediaLibraryEntry.id);
                        history.push(`/movie/${nextVideo.mediaLibraryEntry.id}?autoplay=true`);
                    }
                }}
                autoPlay={queryParams.get("autoplay") === "true"}
                onPlay={() => {
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

        {getCurrentVideo()?.mediaLibraryEntry.id === id && (
            <div className={commonStyles.verticalMargin}>
                <SubHeading>Next in Playlist</SubHeading>
                <p>{peekNextVideo()?.mediaLibraryEntry.name}</p>
                <StyledButton
                    className={`${commonStyles.standardMargin} ${commonStyles.inlineBlock}`}
                    onClick={() => {
                        const nextVideo = getNextVideo();
                        if (nextVideo) {
                            history.push(`/movie/${nextVideo.mediaLibraryEntry.id}?autoplay=true`);
                        }
                    }}
                >
                    Skip to Next
                </StyledButton>
            </div>
        )}
        <SubHeading>Media Information</SubHeading>
        <MediaInfoSection id={id}/>

        <SubHeading>Operations</SubHeading>
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
        <SubHeading>Tags</SubHeading>
        <ul>
            {entry.mediaLibraryEntry.tags.map((tag) => {
                    return <li key={tag}>
                        <TagBadge>
                            {tag}
                            <span onClick={() => {
                                mutateEntry({
                                    tags: entry?.mediaLibraryEntry.tags.filter(item => {
                                        return item != tag
                                    })
                                })
                            }}> x</span>
                        </TagBadge>
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
        <SubHeading>Autotags</SubHeading>
        {entry.autoTags.map((tag) => {
            return <li key={tag}>
                <TagBadge>
                    <Link to={`/mediaLibrary/${tag}`}>{tag}</Link>
                </TagBadge>
            </li>
        })}
        <SubHeading>Duplicate</SubHeading>
        {duplicate ? (
            <div>
                <p>Distance: {duplicate.distance}</p>
                {duplicateEntry?.mediaLibraryEntry ? (
                    <MediaLibraryCard item={duplicateEntry.mediaLibraryEntry}/>
                ) : (
                    <p>Loading duplicate entry or duplicate entry not found.</p>
                )}
            </div>
        ) : (
            <div>
                <p>no duplicate stored.</p>
            </div>
        )}
    </>
}
