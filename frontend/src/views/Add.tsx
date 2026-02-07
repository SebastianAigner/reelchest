import * as React from "react";
import {ChangeEvent, FormEvent, useState} from "react";
import axios from "axios";
import {MainHeading} from "../components/Typography";
import {StyledInput} from "../components/StyledInput";
import {commonStyles} from "../styles/common";

export function Add() {
    const [urlField, setUrlField] = useState("")
    const [submitError, setSubmitError] = useState<string | null>(null)
    let submit = (e: FormEvent<HTMLFormElement>) => {
        console.log(urlField)
        setSubmitError(null)
        addToDownloadList(urlField)
            .then(() => {
                setUrlField("");
            })
            .catch((error) => {
                setSubmitError(error.message ?? "Failed to add download")
            })
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
        {submitError && (
            <div className={`${commonStyles.verticalMargin} ${commonStyles.errorText}`}>
                {submitError}
            </div>
        )}
    </>;
}

export function addToDownloadList(urlField: string) {
    return axios.post("/api/download", {
        url: urlField
    }).catch((error) => {
        const message =
            error?.response?.data?.error ||
            error?.message ||
            "Failed to add download";
        throw new Error(message);
    })
}
