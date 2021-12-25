import * as React from "react";

interface StyledButtonProps {
    // onClick: () => void
    children: React.ReactNode
}

export function StyledButton(props: StyledButtonProps & React.ButtonHTMLAttributes<HTMLButtonElement>) {
    return <button
        {...props}
        className={`block bg-green-500 text-white font-semibold rounded-lg shadow-md focus:outline-none p-2 ${props.className}`}
    >
        {props.children}
    </button>
}

/*
<StyledButton disabled = {}/>
 */