import * as React from "react";
import {ChangeEvent, useEffect, useState} from "react";
import {useHistory, useParams} from "react-router-dom";
import {MediaLibraryCard} from "../components/MediaLibraryCard";
import {StyledButton} from "../components/StyledButton";
import {AutoTaggedMediaLibraryEntry} from "../models/AutoTaggedMediaLibraryEntry";
import useSWR from "swr/esm";
import {fetcher} from "../utils";
import {VList} from "virtua";
import {chunk} from "underscore";

interface MyPair {
    first: string,
    second: number
}

function usePopularTags() {
    const {data, error} = useSWR<Array<MyPair>>(`/api/autotags/popular`, fetcher)
    return {
        popTags: data,
        isLoading: !error && !data,
        isError: error
    }
}


function useMediaLibraryEntries() {
    const {data, error} = useSWR<Array<AutoTaggedMediaLibraryEntry>>(
        "/api/mediaLibrary?auto=true",
        fetcher
    );

    return {
        mediaLibraryEntries: data,
        isLoading: !error && !data,
        isError: error,
    };
}

interface SearchQuery {
    query: string | undefined
}

function MediaLibraryCards({entries}: { entries: AutoTaggedMediaLibraryEntry[] }) {
    return <div className={""}>
        <div className={"grid grid-cols-1 sm:grid-cols-3 gap-8"}>
            {
                entries
                    .slice(0, 100)
                    .map(item =>
                        <MediaLibraryCard item={item.mediaLibraryEntry} key={item.mediaLibraryEntry.id}/>
                    )
            }
        </div>
    </div>;
}

function MediaLibraryEntryGrid({entries}: { entries: AutoTaggedMediaLibraryEntry[] }) {
    return <div className={"flex-1"}>
        <VList className={"grid grid-cols-1 sm:grid-cols-3 gap-8"}>
            {
                chunk(entries, 3).map(items =>
                    <div className={"grid grid-cols-1 sm:grid-cols-3 gap-8"}>
                        {items.map(item =>
                            <MediaLibraryCard item={item.mediaLibraryEntry} key={item.mediaLibraryEntry.id}/>
                        )}
                    </div>
                )
            }
        </VList>
    </div>;
}

function MediaLibraryViewChangeButton({children, onClick}: { children: React.ReactNode; onClick: () => void }) {
    return (
        <StyledButton
            className={"my-4 mx-3"}
            onClick={onClick}>
            {children}
        </StyledButton>
    );
}

export function MediaLibrary() {
    const {query} = useParams<SearchQuery>();
    const [data, setData] = useState<Array<AutoTaggedMediaLibraryEntry>>([])
    const {popTags} = usePopularTags()

    const [searchBarContent, setSearchBarContent] = useState(query ? query : "")
    const {mediaLibraryEntries, isLoading, isError} = useMediaLibraryEntries();

    useEffect(() => {
        if (mediaLibraryEntries) {
            setData(mediaLibraryEntries);
        }
    }, [mediaLibraryEntries]);

    let handleChange = (e: ChangeEvent<HTMLInputElement>) => {
        setSearchBarContent(e.target.value)
    }

    const history = useHistory();
    let searchTerms = searchBarContent.toLowerCase().split(",").map(s => {
        return s.trim()
    })
    if (searchBarContent.length == 0) {
        searchTerms = []
    }

    console.log(searchTerms)
    let videosFilteredBySearchBar = data.filter(item => {
        return searchTerms.every(term => {
            return item.mediaLibraryEntry.name.toLowerCase().includes(term) || item.autoTags.includes(term)
        })
    })

    if (searchBarContent == "notags") {
        videosFilteredBySearchBar = data.filter(item => {
            return item.autoTags.length == 0
        })
    }

    const handleRandomVideoSelection = () => {
        const item = videosFilteredBySearchBar[Math.floor(Math.random() * videosFilteredBySearchBar.length)];
        history.push(`/movie/${item.mediaLibraryEntry.id}`);
    };

    const handleRandomZeroHitsVideo = () => {
        const zeroBois = videosFilteredBySearchBar.filter(item => {
            return item.mediaLibraryEntry.hits == 0;
        });
        const item = zeroBois[Math.floor(Math.random() * zeroBois.length)];
        history.push(`/movie/${item.mediaLibraryEntry.id}`);
    };

    const handleAlphabeticalSort = () => {
        const newthing = [...data].sort((a, b) => a.mediaLibraryEntry.name > b.mediaLibraryEntry.name ? 1 : -1);
        setData(newthing);
    };

    const handleHitSort = () => {
        const newthing = [...data].sort((a, b) => a.mediaLibraryEntry.hits < b.mediaLibraryEntry.hits ? 1 : -1);
        setData(newthing);
    };

    const handleShuffle = () => {
        const newthing = [...data].sort(() => 0.5 - Math.random());
        setData(newthing);
    };

    const [allLinks, setAllLinks] = useState<string[]>([]);

    const handleShowAllLinks = () => {
        if (allLinks.length > 0) {
            setAllLinks([]);
        } else {
            const urls = videosFilteredBySearchBar.map(item => `${window.location.origin}/api/video/${item.mediaLibraryEntry.id}`);
            setAllLinks(urls);
        }
    };

    return <>
        <h2 className={"text-5xl"}>Media Lib</h2>
        <form onSubmit={(e) => {
            e?.preventDefault()
        }}>
            <label>Filter: </label>
            <input type={"text"} value={searchBarContent} onChange={handleChange}/>
        </form>

        <div className={""}>
            <div className={"flex flex-row overflow-x-auto"}>
                <MediaLibraryViewChangeButton onClick={handleRandomVideoSelection}>
                    Random video from selection
                </MediaLibraryViewChangeButton>

                <MediaLibraryViewChangeButton onClick={handleRandomZeroHitsVideo}>
                    Random 0-hit video
                </MediaLibraryViewChangeButton>

                <MediaLibraryViewChangeButton onClick={handleAlphabeticalSort}>
                    Alphabetical Sort
                </MediaLibraryViewChangeButton>

                <MediaLibraryViewChangeButton onClick={handleHitSort}>
                    Hit Sort
                </MediaLibraryViewChangeButton>

                <MediaLibraryViewChangeButton onClick={handleShuffle}>
                    Shuffle
                </MediaLibraryViewChangeButton>

                <MediaLibraryViewChangeButton onClick={handleShowAllLinks}>
                    Show All Links
                </MediaLibraryViewChangeButton>
            </div>
        </div>
        <div>
            {popTags?.slice(0, 10)?.map(tag =>
                <StyledButton key={tag.first} className={"inline-block bg-blue-400 mx-1 my-3"} onClick={() => {
                    setSearchBarContent(tag.first)
                }}>
                    {tag.first} <span className={"text-xs"}>({tag.second})</span>
                </StyledButton>
            )
            }

        </div>

        {
            allLinks.length > 0 && (
                <div className={"mt-4"}>
                    <h3 className={"text-xl mb-2"}>Generated Links:</h3>
                    <div className={"font-mono whitespace-pre-wrap"}>
                        {allLinks.map((link, index) => (
                            <div key={index}>{link}</div>
                        ))}
                    </div>
                </div>
            )
        }
        <MediaLibraryCards entries={videosFilteredBySearchBar}/>
    </>;
}

