import * as React from "react";
import {ReactNode} from "react";

export function MainHeading({children}: { children: ReactNode }) {
    return <h2 className="text-5xl">{children}</h2>;
}

export function SectionHeading({children}: { children: ReactNode }) {
    return <h2 className="text-3xl">{children}</h2>;
}