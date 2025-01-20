import * as React from "react";
import {ReactNode} from "react";
import {commonStyles} from "../styles/common";

export function MainHeading({children}: { children: ReactNode }) {
    return <h2 className={`text-5xl ${commonStyles.pageHeaderMargin}`}>{children}</h2>;
}

export function SectionHeading({children}: { children: ReactNode }) {
    return <h2 className={`text-3xl ${commonStyles.sectionHeaderMargin}`}>{children}</h2>;
}

export function SubHeading({children}: { children: ReactNode }) {
    return <h3 className={`text-2xl ${commonStyles.subHeaderMargin}`}>{children}</h3>;
}

export function SmallText({children}: { children: ReactNode }) {
    return <span className="text-xs">{children}</span>;
}

export function SubSectionHeading({children}: { children: ReactNode }) {
    return <h3 className={`text-xl ${commonStyles.subSectionHeaderMargin}`}>{children}</h3>;
}
