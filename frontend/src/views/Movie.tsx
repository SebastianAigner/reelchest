import {Link, useHistory, useLocation, useParams} from "react-router-dom";
import * as React from "react";
import {useRef, useState} from "react";
import {MediaLibraryEntry} from "../models/MediaLibraryEntry";
import {MainHeading, SubHeading} from "../components/Typography";
import {TagBadge} from "../components/TagBadge";
import {usePlaylist} from "../context/PlaylistContext";
import {useMediaInfo} from "../hooks/useMediaInfo";
import axios from "axios";
import {commonStyles} from "../styles/common";
import {StyledButton} from "../components/StyledButton";
import {MarkForDeletionButton} from "../components/MarkForDeletionButton";
import {mutate} from "swr";
import {useAbortableSWR} from "../hooks/useAbortableSWR";
import {fetcher} from "../utils";
import EdiText from "react-editext";
import {INPUT_ACTION, SimpleInputField} from "../components/SimpleInputField";
import {AutoTaggedMediaLibraryEntry} from "../models/AutoTaggedMediaLibraryEntry";
import * as _ from "underscore";
import {MediaLibraryCard} from "../components/MediaLibraryCard";
import DuplicatesDTO = IoSebi.DuplicatesDTO;

function formatBitRate(bitRate?: number): string | null {
    if (!bitRate || bitRate <= 0) return null;
    const kbps = bitRate / 1000;
    if (kbps >= 1000) return `${(kbps / 1000).toFixed(2)} Mbps`;
    return `${Math.round(kbps)} kbps`;
}

function formatBytes(size?: number): string | null {
    if (!size || size <= 0) return null;
    const units = ["B", "KB", "MB", "GB", "TB"];
    let value = size;
    let unitIndex = 0;
    while (value >= 1024 && unitIndex < units.length - 1) {
        value /= 1024;
        unitIndex += 1;
    }
    return `${value.toFixed(value >= 100 ? 0 : value >= 10 ? 1 : 2)} ${units[unitIndex]}`;
}

function formatFrameRate(frameRate?: number): string | null {
    if (!frameRate || frameRate <= 0) return null;
    return `${frameRate.toFixed(2)} fps`;
}

function MediaInfoSection({id}: { id: string }) {
    const {mimeType, width, height, bitRate, frameRate, fileSize, isLoading} = useMediaInfo(id);
    const formattedBitRate = formatBitRate(bitRate);
    const formattedFrameRate = formatFrameRate(frameRate);
    const formattedFileSize = formatBytes(fileSize);

    if (isLoading) return <p>Loading media information...</p>;

    return (
        <ul>
            <li>MIME Type: {mimeType}</li>
            {width && height && (
                <li>Dimensions: {width}×{height}</li>
            )}
            {formattedFrameRate && (
                <li>Frame rate: {formattedFrameRate}</li>
            )}
            {formattedBitRate && (
                <li>Bitrate: {formattedBitRate}</li>
            )}
            {formattedFileSize && (
                <li>File size: {formattedFileSize}</li>
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
    const {data, error} = useAbortableSWR<DuplicatesDTO>(endpoint, fetcher)
    return {
        duplicate: data,
        isLoading: !error && !data,
        isError: error,
    }
}

function useMediaLibraryEntry(id: string) {
    const endpoint = `/api/mediaLibrary/${id}?auto=true`
    const {data, error} = useAbortableSWR<AutoTaggedMediaLibraryEntry>(endpoint, fetcher)
    return {
        entry: data,
        isLoading: !error && !data,
        isError: error,
        mutateEntry: (newEntry: Partial<MediaLibraryEntry>) => {
            if (!data?.mediaLibraryEntry) return
            const joinedEntry = {...data.mediaLibraryEntry, ...newEntry} as MediaLibraryEntry
            console.log(data.mediaLibraryEntry)
            console.log(joinedEntry)
            return axios.post(`/api/mediaLibrary/${id}`, joinedEntry).then(() => {
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

function formatTime(seconds: number): string {
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);
    return `${hrs.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
}

function generateFfmpegCommand(id: string, markIn: number, markOut: number): string {
    const startTime = formatTime(markIn);
    const duration = formatTime(markOut - markIn);
    const videoUrl = `${window.location.origin}/api/video/${id}.mp4`;
    const outputName = `clip_${startTime.replace(/:/g, 'm')}_to_${formatTime(markOut).replace(/:/g, 'm')}.mp4`;
    return `ffmpeg -ss ${startTime} -i "${videoUrl}" -t ${duration} -c copy ${outputName}`;
}

interface ClipMarkerProps {
    videoRef: React.RefObject<HTMLVideoElement>;
    id: string;
    markIn: number | null;
    markOut: number | null;
    setMarkIn: (time: number | null) => void;
    setMarkOut: (time: number | null) => void;
}

function ClipMarker({videoRef, id, markIn, markOut, setMarkIn, setMarkOut}: ClipMarkerProps) {
    const [copied, setCopied] = useState(false);

    const handleMarkIn = () => {
        if (videoRef.current) {
            setMarkIn(videoRef.current.currentTime);
        }
    };

    const handleMarkOut = () => {
        if (videoRef.current) {
            setMarkOut(videoRef.current.currentTime);
        }
    };

    const handleCopyCommand = async () => {
        if (markIn !== null && markOut !== null && markOut > markIn) {
            const command = generateFfmpegCommand(id, markIn, markOut);
            await navigator.clipboard.writeText(command);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        }
    };

    const canGenerateCommand = markIn !== null && markOut !== null && markOut > markIn;

    return (
        <div className="my-4 p-4 border-2 dark:border-gray-700 rounded-lg bg-gray-50 dark:bg-gray-800">
            <SubHeading>Clip Maker</SubHeading>
            <div className="flex flex-wrap gap-2 mb-3">
                <StyledButton onClick={handleMarkIn}>
                    Mark In
                </StyledButton>
                <StyledButton onClick={handleMarkOut}>
                    Mark Out
                </StyledButton>
                <StyledButton
                    onClick={handleCopyCommand}
                    disabled={!canGenerateCommand}
                >
                    {copied ? 'Copied!' : 'Copy FFMPEG Command'}
                </StyledButton>
            </div>
            <div className="text-sm dark:text-gray-300">
                <p>In: {markIn !== null ? formatTime(markIn) : '--:--:--'}</p>
                <p>Out: {markOut !== null ? formatTime(markOut) : '--:--:--'}</p>
                {markIn !== null && markOut !== null && (
                    <p>Duration: {markOut > markIn ? formatTime(markOut - markIn) : 'Invalid (Out must be after In)'}</p>
                )}
            </div>
            {canGenerateCommand && (
                <div className="mt-3">
                    <p className="text-xs text-gray-500 dark:text-gray-400 mb-1">Command:</p>
                    <code
                        className="block text-xs bg-gray-200 dark:bg-gray-900 p-2 rounded overflow-x-auto whitespace-pre-wrap break-all dark:text-gray-300">
                        {generateFfmpegCommand(id, markIn, markOut)}
                    </code>
                </div>
            )}
        </div>
    );
}

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
    const videoRef = useRef<HTMLVideoElement>(null);
    const [markIn, setMarkIn] = useState<number | null>(null);
    const [markOut, setMarkOut] = useState<number | null>(null);
    if (!entry) return <>
        <p>loading...</p>
    </>
    return <>
        <MainHeading><EdiText value={entry.mediaLibraryEntry.name} type={"text"} onSave={(value) => {
            mutateEntry({name: value})
        }}/></MainHeading>
        {showPlayer && (
            <>
                <video
                    ref={videoRef}
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
                <ClipMarker
                    videoRef={videoRef}
                    id={id}
                    markIn={markIn}
                    markOut={markOut}
                    setMarkIn={setMarkIn}
                    setMarkOut={setMarkOut}
                />
            </>
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
                <MarkForDeletionButton
                    isMarkedForDeletion={entry?.mediaLibraryEntry.markedForDeletion || false}
                    onToggle={(nextMarked) => mutateEntry({markedForDeletion: nextMarked})}
                    disabled={!entry?.mediaLibraryEntry}
                />
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
