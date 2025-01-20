import * as React from "react";
import {useState} from "react";
import {MainHeading} from "../components/Typography";
import {commonStyles} from "../styles/common";
import {StyledButton} from "../components/StyledButton";

export function Upload() {
    const [file, setFile] = useState<File | null>(null);
    const [progress, setProgress] = useState(0);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState(false);
    const [uploadedId, setUploadedId] = useState<string | null>(null);

    const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        if (event.target.files && event.target.files[0]) {
            setFile(event.target.files[0]);
            setError(null);
        }
    };

    const handleUpload = async () => {
        if (!file) {
            setError("Please select a file first");
            return;
        }

        const formData = new FormData();
        formData.append("file", file);

        try {
            const xhr = new XMLHttpRequest();
            xhr.open("POST", "/ul", true);

            xhr.upload.onprogress = (event) => {
                if (event.lengthComputable) {
                    const percentComplete = (event.loaded / event.total) * 100;
                    setProgress(percentComplete);
                }
            };

            xhr.onload = () => {
                if (xhr.status === 200) {
                    try {
                        const response = JSON.parse(xhr.responseText);
                        if (response.id) {
                            setProgress(100);
                            setSuccess(true);
                            setFile(null);
                            setUploadedId(response.id);
                            // Redirect after 2 seconds
                            setTimeout(() => {
                                window.location.href = `/#/movie/${response.id}`;
                            }, 2000);
                        } else if (response.error) {
                            setError(response.error);
                        }
                    } catch (e) {
                        setError("Failed to parse server response");
                    }
                } else {
                    setError("Upload failed: " + xhr.statusText);
                }
            };

            xhr.onerror = () => {
                setError("Upload failed. Please try again.");
            };

            xhr.send(formData);
        } catch (err) {
            setError("Upload failed: " + err);
        }
    };

    return (
        <div>
            <MainHeading>Upload</MainHeading>
            <div className={commonStyles.cardContainer}>
                <div className={commonStyles.flexCol}>
                    <input
                        type="file"
                        onChange={handleFileChange}
                        className={commonStyles.standardMargin}
                    />
                    <StyledButton
                        onClick={handleUpload}
                        className={commonStyles.standardMargin}
                        disabled={!file}
                    >
                        Upload
                    </StyledButton>
                    {progress > 0 && progress < 100 && (
                        <div className={commonStyles.standardMargin}>
                            <div className="w-full bg-gray-200 rounded-full h-2.5">
                                <div
                                    className="bg-blue-600 h-2.5 rounded-full"
                                    style={{width: `${progress}%`}}
                                ></div>
                            </div>
                            <div className="text-center mt-2">{Math.round(progress)}%</div>
                        </div>
                    )}
                    {error && (
                        <div className="text-red-500 mt-2">
                            {error}
                        </div>
                    )}
                    {success && (
                        <div className="text-green-500 mt-2 text-center">
                            Upload successful! Redirecting to media page...
                            <div className="mt-2">
                                <a href={uploadedId ? `/#/movie/${uploadedId}` : '/'}
                                   className="text-blue-500 hover:text-blue-700 underline">
                                    Click here if you're not redirected automatically
                                </a>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
