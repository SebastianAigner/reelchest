import * as React from "react";
import {useCallback, useState} from "react";
import {MainHeading} from "../components/Typography";
import {commonStyles} from "../styles/common";
import {StyledButton} from "../components/StyledButton";

// Types and Interfaces
interface UploadResponse {
    id: string;
    error?: string;
}

interface FileProgress {
    [key: string]: number;
}

interface UploadState {
    files: File[];
    progress: FileProgress;
    error: string | null;
    success: boolean;
    uploadedIds: string[];
}

// Helper Components
const ProgressBar: React.FC<{
    fileName: string;
    percent: number;
}> = ({fileName, percent}) => (
    <div className={commonStyles.standardMargin}>
        <div className="text-sm text-gray-600 dark:text-gray-400 mb-1 truncate">{fileName}</div>
        <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2.5">
            <div
                className="bg-blue-600 dark:bg-blue-500 h-2.5 rounded-full transition-all duration-300"
                style={{width: `${percent}%`}}
            />
        </div>
        <div className="text-center mt-1 text-sm">{Math.round(percent)}%</div>
    </div>
);

const UploadStatus: React.FC<{
    error: string | null;
    success: boolean;
    uploadedIds: string[];
}> = ({error, success, uploadedIds}) => {
    if (error) {
        return (
            <div className="text-red-500 mt-2">
                {error}
            </div>
        );
    }

    if (success) {
        return (
            <div className="text-green-500 mt-2 text-center">
                {uploadedIds.length === 1
                    ? "Upload successful! Redirecting to media page..."
                    : `Successfully uploaded ${uploadedIds.length} files! Redirecting to library...`}
                <div className="mt-2">
                    <a href={uploadedIds.length === 1 ? `/#/movie/${uploadedIds[0]}` : '/'}
                       className="text-blue-500 hover:text-blue-700 underline">
                        Click here if you're not redirected automatically
                    </a>
                </div>
            </div>
        );
    }

    return null;
};

// File Upload Components
const FileDropZone: React.FC<{
    onFilesAdded: (files: File[]) => void;
    isDragging: boolean;
    setIsDragging: (isDragging: boolean) => void;
    files: File[];
    onFileRemove: (index: number) => void;
}> = ({onFilesAdded, isDragging, setIsDragging, files, onFileRemove}) => {
    const handleDragEvent = useCallback((e: React.DragEvent<HTMLDivElement>, isDragging?: boolean) => {
        e.preventDefault();
        e.stopPropagation();
        if (isDragging !== undefined) setIsDragging(isDragging);
    }, [setIsDragging]);

    const handleDrop = useCallback((e: React.DragEvent<HTMLDivElement>) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragging(false);
        const droppedFiles = Array.from(e.dataTransfer.files);
        onFilesAdded(droppedFiles);
    }, [setIsDragging, onFilesAdded]);

    const handleFileChange = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
        const selectedFiles = event.target.files ? Array.from(event.target.files) : [];
        onFilesAdded(selectedFiles);
    }, [onFilesAdded]);

    return (
        <div
            className="mx-3 my-4"
            onDragEnter={(e) => handleDragEvent(e, true)}
            onDragOver={(e) => handleDragEvent(e)}
            onDragLeave={(e) => handleDragEvent(e, false)}
            onDrop={handleDrop}
        >
            <div
                className={`w-full bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-200 rounded-lg shadow-lg border-2 ${isDragging ? 'border-green-500 dark:border-green-600' : 'border-gray-300 dark:border-gray-700'} transition-colors`}>
                <FileInput onFileChange={handleFileChange} filesCount={files.length}/>
                <FileList files={files} onRemove={onFileRemove}/>
            </div>
        </div>
    );
};

const FileInput: React.FC<{
    onFileChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
    filesCount: number;
}> = ({onFileChange, filesCount}) => (
    <label className="block w-full cursor-pointer">
        <div className="flex flex-col items-center px-4 py-6 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
            <svg className="w-8 h-8 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"
                 xmlns="http://www.w3.org/2000/svg">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2"
                      d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"/>
            </svg>
            <span className="mt-2 text-base leading-normal">
                {filesCount > 0 ? `${filesCount} file${filesCount === 1 ? '' : 's'} selected` : 'Drag and drop files here or click to select'}
            </span>
            <span className="mt-1 text-sm text-gray-500 dark:text-gray-400">Upload your media files</span>
            <input
                type="file"
                multiple
                className="hidden"
                onChange={onFileChange}
                aria-label="File upload"
            />
        </div>
    </label>
);

const FileList: React.FC<{
    files: File[];
    onRemove: (index: number) => void;
}> = ({files, onRemove}) => {
    if (files.length === 0) return null;

    return (
        <div className="border-t-2 border-gray-100 dark:border-gray-700">
            <div className="w-full max-h-40 overflow-y-auto px-4 py-2">
                {files.map((file, index) => (
                    <div key={`${file.name}-${index}`}
                         className="flex items-center justify-between p-2 mt-2 first:mt-0 bg-gray-50 dark:bg-gray-700 rounded">
                        <span className="truncate flex-1 mr-2">{file.name}</span>
                        <button
                            onClick={(e) => {
                                e.preventDefault();
                                onRemove(index);
                            }}
                            className="text-red-500 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
                            aria-label={`Remove ${file.name}`}
                        >
                            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2"
                                      d="M6 18L18 6M6 6l12 12"/>
                            </svg>
                        </button>
                    </div>
                ))}
            </div>
        </div>
    );
};

// Main Upload Component
export function Upload(): JSX.Element {
    const [state, setState] = useState<UploadState>({
        files: [],
        progress: {},
        error: null,
        success: false,
        uploadedIds: [],
    });
    const [isDragging, setIsDragging] = useState(false);

    const addFiles = useCallback((newFiles: File[]) => {
        setState(prevState => {
            const uniqueNewFiles = newFiles.filter(newFile =>
                !prevState.files.some(existingFile =>
                    existingFile.name === newFile.name &&
                    existingFile.size === newFile.size
                )
            );
            return {
                ...prevState,
                files: [...prevState.files, ...uniqueNewFiles],
                error: null
            };
        });
    }, []);

    const removeFile = useCallback((index: number) => {
        setState(prevState => ({
            ...prevState,
            files: prevState.files.filter((_, i) => i !== index)
        }));
    }, []);

    const uploadFile = useCallback(async (file: File): Promise<string> => {
        if (!file) {
            throw new Error("No file provided");
        }

        const formData = new FormData();
        formData.append("file", file);

        const updateProgress = (percent: number) => {
            setState(prevState => ({
                ...prevState,
                progress: {
                    ...prevState.progress,
                    [file.name]: percent
                }
            }));
        };

        return new Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();

            xhr.upload.addEventListener('progress', (event) => {
                if (event.lengthComputable) {
                    const percent = (event.loaded / event.total) * 100;
                    updateProgress(percent);
                }
            });

            xhr.addEventListener('load', () => {
                if (xhr.status >= 200 && xhr.status < 300) {
                    try {
                        const response: UploadResponse = JSON.parse(xhr.responseText);
                        if (response.error) {
                            reject(new Error(response.error));
                        } else {
                            resolve(response.id);
                        }
                    } catch (error) {
                        reject(new Error('Invalid response format'));
                    }
                } else {
                    reject(new Error(`Upload failed: ${xhr.statusText || 'Unknown error'}`));
                }
            });

            xhr.addEventListener('error', () => {
                reject(new Error('Network error occurred'));
            });

            xhr.addEventListener('abort', () => {
                reject(new Error('Upload was aborted'));
            });

            xhr.open('POST', '/ul');
            xhr.send(formData);
        });
    }, []);

    const UploadStatus: React.FC<{
        error: string | null;
        success: boolean;
        uploadedIds: string[];
    }> = ({error, success, uploadedIds}) => {
        if (error) {
            return (
                <div className="text-red-500 mt-2">
                    {error}
                </div>
            );
        }

        if (success) {
            return (
                <div className="text-green-500 mt-2 text-center">
                    {uploadedIds.length === 1
                        ? "Upload successful! Redirecting to media page..."
                        : `Successfully uploaded ${uploadedIds.length} files! Redirecting to library...`}
                    <div className="mt-2">
                        <a href={uploadedIds.length === 1 ? `/#/movie/${uploadedIds[0]}` : '/'}
                           className="text-blue-500 hover:text-blue-700 underline">
                            Click here if you're not redirected automatically
                        </a>
                    </div>
                </div>
            );
        }

        return null;
    };

    const handleUpload = useCallback(async () => {
        // Validate files
        if (state.files.length === 0) {
            setState(prevState => ({
                ...prevState,
                error: "Please select at least one file"
            }));
            return;
        }

        // Reset state before upload
        setState(prevState => ({
            ...prevState,
            error: null,
            progress: {},
            success: false,
            uploadedIds: []
        }));

        try {
            // Upload all files concurrently
            const uploadPromises = state.files.map(file => {
                return uploadFile(file).catch(error => {
                    throw new Error(`Failed to upload ${file.name}: ${error.message}`);
                });
            });

            const uploadedIds = await Promise.all(uploadPromises);

            // Update state with success
            setState(prevState => ({
                ...prevState,
                uploadedIds,
                success: true,
                files: [],
            }));

            // Navigate after successful upload
            const redirectTimeout = setTimeout(() => {
                window.location.href = uploadedIds.length === 1
                    ? `/#/movie/${uploadedIds[0]}`
                    : '/#/';
            }, 2000);

            // Cleanup timeout if component unmounts
            return () => clearTimeout(redirectTimeout);
        } catch (err) {
            // Handle specific error types
            const errorMessage = err instanceof Error
                ? err.message
                : "Upload failed due to an unexpected error";

            setState(prevState => ({
                ...prevState,
                error: errorMessage,
                success: false
            }));
        }
    }, [state.files, uploadFile]);

    return (
        <div>
            <MainHeading>Upload</MainHeading>
            <div className="max-w-2xl mx-auto">
                <div className={commonStyles.cardContainer}>
                <div className={commonStyles.flexCol}>
                    <FileDropZone
                        onFilesAdded={addFiles}
                        isDragging={isDragging}
                        setIsDragging={setIsDragging}
                        files={state.files}
                        onFileRemove={removeFile}
                    />
                    <StyledButton
                        onClick={handleUpload}
                        className={commonStyles.standardMargin}
                        disabled={state.files.length === 0}
                    >
                        Upload
                    </StyledButton>
                    {Object.entries(state.progress).map(([fileName, percent]) => (
                        <ProgressBar
                            key={fileName}
                            fileName={fileName}
                            percent={percent}
                        />
                    ))}
                    <UploadStatus
                        error={state.error}
                        success={state.success}
                        uploadedIds={state.uploadedIds}
                    />
                </div>
                </div>
            </div>
        </div>
    );
}
