import * as React from "react";
import {ChangeEvent, FormEvent, useState} from "react";
import axios from "axios";

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
        <h2 className={"text-5xl"}>Add</h2>
        <form onSubmit={submit}>
            <label>Link: </label>
            <input type={"text"} value={urlField} onChange={handleChange}/>
        </form>
    </>;
}

export function addToDownloadList(urlField: string) {
    return axios.post("/api/download", {
        url: urlField
    })
}