import * as React from "react";
import {ChangeEvent, FormEvent, useState} from "react";
import axios from "axios";
import {MainHeading} from "../components/Typography";
import {StyledInput} from "../components/StyledInput";

export function Add() {
    const [urlField, setUrlField] = useState("")
    let submit = (e: FormEvent<HTMLFormElement>) => {
        console.log(urlField)
        addToDownloadList(urlField);
        setUrlField("");
        e.preventDefault()
    }

    let handleChange = (e: ChangeEvent<HTMLInputElement>) => {
        setUrlField(e.target.value)
    }
    return <>
        <MainHeading>Add</MainHeading>
        <form onSubmit={submit}>
            <label>Link: </label>
            <StyledInput
                type="text"
                value={urlField}
                onChange={handleChange}
            />
        </form>
    </>;
}

export function addToDownloadList(urlField: string) {
    return axios.post("/api/download", {
        url: urlField
    })
}
