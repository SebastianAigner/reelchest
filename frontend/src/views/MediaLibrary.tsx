import * as React from "react";
import {ChangeEvent, useEffect, useState} from "react";
import axios from "axios";
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

function usePopTags() {
    const {data, error} = useSWR<Array<MyPair>>(`/api/autotags/popular`, fetcher)
    return {
        popTags: data,
        isLoading: !error && !data,
        isError: error
    }
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

export function MediaLibrary() {
    const {query} = useParams<SearchQuery>();
    const [data, setData] = useState<Array<AutoTaggedMediaLibraryEntry>>([])
    const {popTags} = usePopTags()

    const [searchBarContent, setSearchBarContent] = useState(query ? query : "")
    useEffect(() => {
        const fetchData = async () => {
            const result = await axios.get<Array<AutoTaggedMediaLibraryEntry>>("/api/mediaLibrary?auto=true");
            console.log(result.data);
            setData(result.data);
        }
        fetchData()
    }, [])

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

    return (<>
        <h2 className={"text-5xl"}>Media Lib</h2>
        <form onSubmit={(e) => {
            e?.preventDefault()
        }}>
            <label>Filter: </label>
            <input type={"text"} value={searchBarContent} onChange={handleChange}/>
        </form>

        <div className={""}>
            <div className={"flex flex-row overflow-x-scroll"}>
                <StyledButton
                    className={"my-4 mx-3"}
                    onClick={() => {
                        const item = videosFilteredBySearchBar[Math.floor(Math.random() * videosFilteredBySearchBar.length)];
                        history.push(`/movie/${item.mediaLibraryEntry.id}`)
                    }}>
                    Random video from selection
                </StyledButton>

                <StyledButton
                    className={"my-4 mx-3"}
                    onClick={() => {
                        const zeroBois = videosFilteredBySearchBar.filter(item => {
                            return item.mediaLibraryEntry.hits == 0
                        })
                        const item = zeroBois[Math.floor(Math.random() * zeroBois.length)];
                        history.push(`/movie/${item.mediaLibraryEntry.id}`)
                    }}>
                    Random 0-hit video
                </StyledButton>
                <StyledButton
                    className={"my-4 mx-3"}
                    onClick={() => {
                        console.log("sorting!")
                        const newthing = [...data].sort((a, b) => (a.mediaLibraryEntry.name > b.mediaLibraryEntry.name) ? 1 : -1)
                        console.log(newthing)
                        setData(newthing)
                    }}>
                    Alphabetical Sort
                </StyledButton>

                <StyledButton
                    className={"my-4 mx-3"}
                    onClick={() => {
                        console.log("sorting!")
                        const newthing = [...data].sort((a, b) => (a.mediaLibraryEntry.hits < b.mediaLibraryEntry.hits) ? 1 : -1)
                        console.log(newthing)
                        setData(newthing)
                    }}>
                    Hit Sort
                </StyledButton>
                <StyledButton
                    className={"my-4 mx-3"}
                    onClick={() => {
                        const newthing = [...data].sort(() => 0.5 - Math.random())
                        setData(newthing)
                    }}>
                    Shuffle
                </StyledButton>
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
        <MediaLibraryCards entries={videosFilteredBySearchBar}/>
    </>);
}