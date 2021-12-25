import * as React from "react";
import {useEffect, useState} from "react";
import {SearchResult} from "../models/SearchResult";
import axios from "axios";
import {SearchResultComponent} from "../components/SearchResultComponent";
import {StyledButton} from "../components/StyledButton";
import {INPUT_ACTION, SimpleInputField} from "../components/SimpleInputField";
import useSWR from "swr/esm";
import {fetcher} from "../utils";

export function Search() {
    const {data, error} = useSWR<Array<string>>(`/api/searchers`, fetcher)

    const [searchUrl, setSearchUrl] = useState<string>("")
    const [results, setResults] = useState<Array<SearchResult>>()
    const [pageOffset, setPageOffset] = useState(0);
    const [term, setTerm] = useState("");


    useEffect(() => {
        if (data) {
            setSearchUrl(data[0])
        }
    }, [data])

    let submit = (query: String, pageOffset: number | null) => {
        console.log(query)
        setResults([])
        axios.post<Array<SearchResult>>(`/api/search/${searchUrl}`, {
            term: query,
            offset: pageOffset
        }).then(
            (response) => {
                setResults(response.data)
                console.log(response);
            }, (error) => {
                console.log(error);
            }
        )
    }

    return <>
        <h2 className={"text-5xl"}>Search</h2>
        <select value={searchUrl} onChange={(event => {
            setSearchUrl(event.target.value);
            event.preventDefault()
        })}>
            {data?.map((value) =>
                <option value={value}>{value}</option>
            )}
        </select>
        <SimpleInputField
            onChange={(value) => {
            }}
            onSubmit={(value) => {
                setTerm(value)
                setPageOffset(0);
                submit(value, 0);
                return INPUT_ACTION.DO_NOTHING
            }}/>
        <div className={"grid grid-cols-1 sm:grid-cols-3 gap-8"}>
            {results?.map((result) =>
                <SearchResultComponent key={result.url} {...result}/>
            )}
        </div>
        <div>
            <StyledButton
                className={"my-4 inline-block"}
                onClick={() => {
                    submit(term, pageOffset - 30)
                    setPageOffset(pageOffset - 30)
                    window.scrollTo(0, 0);
                }}>
                Previous
            </StyledButton>
            <span>{pageOffset / 30}</span>
            <StyledButton
                className={"my-4 inline-block"}
                onClick={() => {
                    submit(term, pageOffset + 30)
                    setPageOffset(pageOffset + 30)
                    window.scrollTo(0, 0);
                }}>
                Next
            </StyledButton>
        </div>
    </>;
}