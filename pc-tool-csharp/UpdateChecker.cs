using System;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;

namespace InputBlockerSetup;

public class UpdateChecker
{
    private const string GITHUB_API = "https://api.github.com/repos/Laviesss/InputBlocker/releases/latest";
    
    public class UpdateInfo
    {
        public string Version { get; set; } = "";
        public string ReleaseUrl { get; set; } = "";
        public string Body { get; set; } = "";
        public string PublishedAt { get; set; } = "";
    }
    
    public interface IUpdateCallback
    {
        void OnUpdateAvailable(UpdateInfo info, string currentVersion);
        void OnNoUpdateAvailable(string currentVersion);
        void OnError(string error);
    }
    
    public static async Task CheckForUpdateAsync(IUpdateCallback callback, string currentVersion)
    {
        try
        {
            using var client = new HttpClient();
            client.DefaultRequestHeaders.Add("User-Agent", "InputBlocker-CSharp");
            client.Timeout = TimeSpan.FromSeconds(10);
            
            var response = await client.GetStringAsync(GITHUB_API);
            var json = JsonDocument.Parse(response);
            
            var root = json.RootElement;
            var tagName = root.GetProperty("tag_name").GetString() ?? "";
            var version = tagName.StartsWith("v") ? tagName.Substring(1) : tagName;
            var releaseUrl = root.GetProperty("html_url").GetString() ?? "";
            
            var updateInfo = new UpdateInfo
            {
                Version = version,
                ReleaseUrl = releaseUrl,
                Body = root.TryGetProperty("body", out var body) ? body.GetString() ?? "" : "",
                PublishedAt = root.TryGetProperty("published_at", out var date) ? date.GetString() ?? "" : ""
            };
            
            if (IsNewerVersion(updateInfo.Version, currentVersion))
            {
                callback.OnUpdateAvailable(updateInfo, currentVersion);
            }
            else
            {
                callback.OnNoUpdateAvailable(currentVersion);
            }
        }
        catch (Exception ex)
        {
            callback.OnError(ex.Message);
        }
    }
    
    private static bool IsNewerVersion(string latest, string current)
    {
        var latestParts = latest.Split('.').Select(p => int.TryParse(p, out var n) ? n : 0).ToArray();
        var currentParts = current.Split('.').Select(p => int.TryParse(p, out var n) ? n : 0).ToArray();
        
        var maxLen = Math.Max(latestParts.Length, currentParts.Length);
        
        for (int i = 0; i < maxLen; i++)
        {
            var latestNum = i < latestParts.Length ? latestParts[i] : 0;
            var currentNum = i < currentParts.Length ? currentParts[i] : 0;
            
            if (latestNum > currentNum) return true;
            if (latestNum < currentNum) return false;
        }
        
        return false;
    }
}
