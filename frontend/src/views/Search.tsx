import * as React from "react";
import {useEffect, useState} from "react";
import {SearchResult} from "../models/SearchResult";
import axios from "axios";
import {SearchResultComponent} from "../components/SearchResultComponent";
import {StyledButton} from "../components/StyledButton";
import {MainHeading} from "../components/Typography";
import {HStack, ResponsiveGrid} from "../components/Layout";
import {commonStyles} from "../styles/common";
import {INPUT_ACTION, SimpleInputField} from "../components/SimpleInputField";
import useSWR from "swr";
import {fetcher} from "../utils";
import {LoadingSpinner} from "../components/LoadingSpinner";

export function Search() {
    const {data, error} = useSWR<Array<string>>(`/api/searchers`, fetcher)

    const [searchUrl, setSearchUrl] = useState<string>("")
    const [results, setResults] = useState<Array<SearchResult>>()
    const [pageOffset, setPageOffset] = useState(0);
    const [pageSize, setPageSize] = useState(30);
    const [term, setTerm] = useState("");
    const [isLoading, setIsLoading] = useState(false);
    const [searchError, setSearchError] = useState<string | null>(null);


    useEffect(() => {
        if (data) {
            setSearchUrl(data[0])
        }
    }, [data])

    let submit = (query: String, pageOffset: number | null) => {
        console.log(query)
        setResults([])
        setSearchError(null)
        setIsLoading(true)
        axios.post<Array<SearchResult>>(`/api/search/${searchUrl}`, {
            term: query,
            offset: pageOffset
        }).then(
            (response) => {
                setResults(response.data)
                if (pageOffset === 0 && response.data.length > 0) {
                    setPageSize(response.data.length)
                }
                setIsLoading(false)
                console.log(response);
            }, (error) => {
                setSearchError(error.message || "An error occurred while searching")
                setIsLoading(false)
                console.log(error);
            }
        )
    }

    return <>
        <MainHeading>Search</MainHeading>
        <HStack><select value={searchUrl} onChange={(event => {
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
                }}/></HStack>
        {isLoading && <LoadingSpinner/>}
        {searchError && (
            <div className={`${commonStyles.verticalMargin} ${commonStyles.errorText}`}>
                {searchError}
            </div>
        )}
        {!isLoading && !searchError && results && results.length === 0 && (
            <div className={commonStyles.verticalMargin}>
                No results found
            </div>
        )}
        <ResponsiveGrid>
            {results?.map((result) =>
                <SearchResultComponent key={result.url} {...result}/>
            )}
        </ResponsiveGrid>
        <div>
            <StyledButton
                disabled={isLoading || !!searchError || pageOffset === 0 || !results}
                className={`${commonStyles.verticalMargin} ${commonStyles.inlineBlock}`}
                onClick={() => {
                    submit(term, pageOffset - pageSize)
                    setPageOffset(pageOffset - pageSize)
                    window.scrollTo(0, 0);
                }}>
                Previous
            </StyledButton>
            <span>{pageOffset / pageSize}</span>
            <StyledButton
                disabled={isLoading || !!searchError || !results || results.length < pageSize}
                className={`${commonStyles.verticalMargin} ${commonStyles.inlineBlock}`}
                onClick={() => {
                    submit(term, pageOffset + pageSize)
                    setPageOffset(pageOffset + pageSize)
                    window.scrollTo(0, 0);
                }}>
                Next
            </StyledButton>
        </div>
    </>;
}
