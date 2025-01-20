import * as React from "react";
import {ChangeEvent} from "react";

interface StyledInputProps {
    type: "text" | "number" | "file";
    value?: string | number;
    name?: string;
    multiple?: boolean;
    onChange?: (e: ChangeEvent<HTMLInputElement>) => void;
}

const baseInputStyles = "bg-white dark:bg-gray-800 text-gray-900 dark:text-white border border-gray-300 dark:border-gray-600 rounded p-2";
const fileInputStyles = "block w-full text-gray-900 dark:text-white file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:bg-gray-100 dark:file:bg-gray-700 file:text-gray-900 dark:file:text-white hover:file:bg-gray-200 dark:hover:file:bg-gray-600";

export function StyledInput(props: StyledInputProps) {
    const {type, ...rest} = props;
    const className = type === "file" ? fileInputStyles : baseInputStyles;

    return (
        <input
            type={type}
            className={className}
            {...rest}
        />
    );
}