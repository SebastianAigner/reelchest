import React, {useEffect, useState} from 'react';

interface DHashStats {
    total_movies: number;
    movies_with_dhashes: number;
    movies_missing_dhashes: number;
    missing_dhashes_list: Array<{
        id: string;
        name: string;
        path: string | null;
    }>;
}

export default function DHashDebug() {
    const [stats, setStats] = useState<DHashStats | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        fetch('/api/mediaLibrary/debug/dhash-stats')
            .then(response => {
                if (!response.ok) {
                    throw new Error('Failed to fetch dhash statistics');
                }
                return response.json();
            })
            .then(data => {
                setStats(data);
                setLoading(false);
            })
            .catch(err => {
                setError(err.message);
                setLoading(false);
            });
    }, []);

    if (loading) {
        return <div>Loading dhash statistics...</div>;
    }

    if (error) {
        return <div>Error: {error}</div>;
    }

    if (!stats) {
        return <div>No data available</div>;
    }

    return (
        <div className="container mx-auto p-4">
            <h1 className="text-2xl font-bold mb-4">DHash Statistics</h1>

            <div className="grid grid-cols-3 gap-4 mb-8">
                <div className="bg-white dark:bg-gray-800 p-4 rounded shadow">
                    <h2 className="text-lg font-semibold mb-2">Total Movies</h2>
                    <p className="text-3xl">{stats.total_movies}</p>
                </div>
                <div className="bg-white dark:bg-gray-800 p-4 rounded shadow">
                    <h2 className="text-lg font-semibold mb-2">With DHashes</h2>
                    <p className="text-3xl text-green-600 dark:text-green-400">{stats.movies_with_dhashes}</p>
                </div>
                <div className="bg-white dark:bg-gray-800 p-4 rounded shadow">
                    <h2 className="text-lg font-semibold mb-2">Missing DHashes</h2>
                    <p className="text-3xl text-red-600 dark:text-red-400">{stats.movies_missing_dhashes}</p>
                </div>
            </div>

            <div className="bg-white dark:bg-gray-800 p-4 rounded shadow">
                <h2 className="text-xl font-semibold mb-4">Movies Missing DHashes</h2>
                <div className="overflow-x-auto">
                    <table className="min-w-full table-auto">
                        <thead>
                        <tr className="bg-gray-100 dark:bg-gray-700">
                            <th className="px-4 py-2 text-left">Name</th>
                            <th className="px-4 py-2 text-left">Path</th>
                        </tr>
                        </thead>
                        <tbody>
                        {stats.missing_dhashes_list.map(movie => (
                            <tr key={movie.id} className="border-b dark:border-gray-700">
                                <td className="px-4 py-2">{movie.name}</td>
                                <td className="px-4 py-2 text-gray-600 dark:text-gray-400">{movie.path || 'N/A'}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
