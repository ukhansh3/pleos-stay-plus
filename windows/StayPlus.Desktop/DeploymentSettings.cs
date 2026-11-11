namespace StayPlus.Desktop;

internal static class DeploymentSettings
{
    internal static string Prefer(string? runtime, string? embedded) =>
        !string.IsNullOrWhiteSpace(runtime) ? runtime.Trim() : embedded?.Trim() ?? "";

    internal static bool AllowsApi(Uri endpoint, bool development) =>
        endpoint.Scheme == "https" || (development && endpoint.Scheme == "http" && endpoint.IsLoopback);
}
