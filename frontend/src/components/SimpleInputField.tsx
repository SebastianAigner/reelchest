import * as React from "react";
import {ChangeEvent, useState} from "react";

export enum INPUT_ACTION {
    DO_NOTHING,
    CLEAR
}

export interface SimpleInputFieldProps {
    onChange?(value: string): void

    onSubmit?(value: string): INPUT_ACTION | void
}

export function SimpleInputField(props: SimpleInputFieldProps) {
    const [inputFieldValue, setInputFieldValue] = useState("")

    return <>
        <form onSubmit={(e) => {
            const action = props.onSubmit?.(inputFieldValue)
            if (action == INPUT_ACTION.CLEAR) {
                setInputFieldValue("")
            }
            e?.preventDefault()
        }}>
            <label>Enter: </label>
            <input type={"text"} value={inputFieldValue} onChange={(e: ChangeEvent<HTMLInputElement>) => {
                props.onChange?.(e.target.value)
                setInputFieldValue(e.target.value)
            }}/>
        </form>
    </>
}