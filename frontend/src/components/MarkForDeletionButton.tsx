import * as React from "react";
import {useEffect, useState} from "react";

interface MarkForDeletionButtonProps {
    isMarkedForDeletion: boolean;
    onToggle: (nextMarked: boolean) => Promise<void> | void;
    className?: string;
    disabled?: boolean;
    markLabel?: string;
    unmarkLabel?: string;
    markInProgressLabel?: string;
    unmarkInProgressLabel?: string;
    title?: string;
}

export function MarkForDeletionButton({
    isMarkedForDeletion,
    onToggle,
    className,
    disabled,
    markLabel = "Mark for deletion",
    unmarkLabel = "Unmark for deletion",
    markInProgressLabel = "Marking for deletion...",
    unmarkInProgressLabel = "Unmarking for deletion...",
    title,
}: MarkForDeletionButtonProps) {
    const [pendingTarget, setPendingTarget] = useState<boolean | null>(null);
    const [confirmedMarked, setConfirmedMarked] = useState(isMarkedForDeletion);
    const [awaitingPropSync, setAwaitingPropSync] = useState(false);

    useEffect(() => {
        if (awaitingPropSync && isMarkedForDeletion === confirmedMarked) {
            setAwaitingPropSync(false);
            return;
        }
        if (!awaitingPropSync && pendingTarget === null && isMarkedForDeletion !== confirmedMarked) {
            setConfirmedMarked(isMarkedForDeletion);
        }
    }, [awaitingPropSync, confirmedMarked, isMarkedForDeletion, pendingTarget]);

    const handleClick = async () => {
        if (disabled || pendingTarget !== null) return;
        const nextMarked = !confirmedMarked;
        setPendingTarget(nextMarked);

        try {
            await Promise.resolve(onToggle(nextMarked));
            setConfirmedMarked(nextMarked);
            setAwaitingPropSync(true);
        } catch (error) {
            console.error("Failed to toggle deletion state", error);
        } finally {
            setPendingTarget(null);
        }
    };

    const label = pendingTarget !== null
        ? (pendingTarget ? markInProgressLabel : unmarkInProgressLabel)
        : (confirmedMarked ? unmarkLabel : markLabel);

    return (
        <button
            type="button"
            className={className}
            onClick={handleClick}
            disabled={disabled || pendingTarget !== null}
            aria-busy={pendingTarget !== null}
            title={title}
        >
            {label}
        </button>
    );
}
