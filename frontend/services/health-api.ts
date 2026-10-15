export async function getBackendStatus(): Promise<string> {
    const response = await fetch("/api/health");

    if (!response.ok) {
        throw new Error("Backend is not available");
    }

    return response.text();
}
