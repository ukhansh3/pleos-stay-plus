using System.ComponentModel;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace StayPlus.Desktop;

public partial class MainWindow
{
    // These are public deployment settings. Authentication remains in IdentityProvider.
    private void SetDisplayDefaults()
    {
        Server.Text = Environment.GetEnvironmentVariable("STAYPLUS_DESKTOP_API_URL") ?? "";
        AuthUrl.Text = Environment.GetEnvironmentVariable("STAYPLUS_DESKTOP_AUTH_URL") ?? "";
        PublicKey.Text = Environment.GetEnvironmentVariable("STAYPLUS_DESKTOP_PUBLISHABLE_KEY") ?? "";
    }

    private string? vehicleId;
    private string? vehicleName;


    private static string Field(JsonNode? data, string key) => data?[key]?.GetValue<string>() ?? "";
    private static string DateLabel(JsonNode? data, string key)
        => DateTimeOffset.TryParse(Field(data, key), out var date)
            ? date.ToLocalTime().ToString("yyyy.MM.dd HH:mm") : "날짜 확인 불가";

    private sealed class Item(string id, string label, JsonNode data) : INotifyPropertyChanged
    {
        public string Id { get; } = id;
        public string Label { get; } = label;
        public JsonNode Data { get; } = data;
        public string Name => Label;
        public override string ToString() => Label;
        public string Type => Path.GetExtension(Label).TrimStart('.').ToUpperInvariant();
        public string Date { get; private set; } = "등록  " + DateLabel(data, "createdAt");
        public void ShowLatest(JsonNode? latest)
        {
            if (latest == null) return;
            Date = "최근 버전  " + DateLabel(latest, "committedAt");
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Date)));
        }
        public event PropertyChangedEventHandler? PropertyChanged;
    }

    private static string VersionLabel(JsonNode version)
    {
        long bytes = version["size"]!.GetValue<long>();
        string size = bytes < 1024 ? $"{bytes:N0} B" : bytes < 1048576 ? $"{bytes / 1024d:N1} KB" : $"{bytes / 1048576d:N1} MB";
        string kind = Field(version, "kind") == "ORIGINAL" ? "원본" : "수정본";
        return $"{kind}  v{version["versionNo"]}   ·   {version["fileName"]}   ·   {size}   ·   {DateLabel(version, "committedAt")}";
    }

    private string DescribePair(JsonNode? pair)
    {
        var peer = new[] { pair?["issuer"], pair?["claimant"] }.FirstOrDefault(n => Field(n, "kind") == "VEHICLE");
        vehicleId = peer?["id"]?.GetValue<string>();
        vehicleName = peer?["name"]?.GetValue<string>();
        var phase = Field(pair, "state");
        var pc = new[] { pair?["issuer"], pair?["claimant"] }.FirstOrDefault(n => Field(n, "kind") == "WINDOWS");
        var pcId = Field(pc, "id");
        bool alreadyApproved = pcId != null && pair?["approvedDeviceIds"] is JsonArray approvals &&
            approvals.Any(value => value?.GetValue<string>() == pcId);
        ApproveVehicle.IsEnabled = phase == "CLAIMED" && vehicleName != null && !alreadyApproved;
        var state = phase switch
        {
            "CLAIMED" when alreadyApproved => "차량 승인을 기다리고 있습니다.",
            "CLAIMED" => "차량을 확인한 뒤 승인해 주세요.",
            "CONNECTED" => "양쪽 기기의 승인이 완료됐습니다.",
            "EXPIRED" => "코드가 만료됐습니다. 새 코드를 발급해 주세요.",
            "CANCELLED" => "연결 요청이 취소됐습니다.",
            _ => "상대 기기에서 코드를 입력하기를 기다리고 있습니다."
        };
        return (vehicleName == null ? "" : vehicleName + " · ") + state;
    }

    private void DisplayConnection(JsonNode? connection)
    {
        bool linked = connectionId != null;
        string? name = null;
        // The current-connection response has IDs only. Cache a verified label against its connection.
        if (linked)
        {
            try
            {
                if (vehicleName != null && vehicleId == Field(connection, "vehicleDeviceId"))
                {
                    name = vehicleName;
                    TokenStore.WriteSecret("display-vehicle", new JsonObject { ["id"] = connectionId, ["name"] = name }.ToJsonString());
                }
                else if (TokenStore.ReadSecret("display-vehicle") is string label)
                {
                    var cached = JsonNode.Parse(label);
                    if (Field(cached, "id") == connectionId) name = Field(cached, "name");
                }
            }
            catch { /* A missing display preference does not invalidate a working connection. */ }
        }
        Connection.Text = linked ? (name ?? "차량") + " · 연결됨" : "차량 연결 안 됨";
        Connection.ToolTip = Connection.Text;
        VehicleTitle.Text = linked ? name ?? "연결된 차량" : "연결된 차량이 없습니다";
        VehicleNote.Text = linked ? "이 PC와 파일을 주고받을 수 있습니다." : "차량에 표시된 코드를 입력하세요.";
        Unlink.IsEnabled = linked;
        EmptyTitle.Text = linked ? "보관함이 비어 있습니다" : "차량과 파일을 공유하세요";
        EmptyNote.Text = linked ? "새로 고침으로 보관함을 확인하거나 첫 문서를 올리세요." : "차량을 연결한 다음 문서를 보낼 수 있습니다.";
        LinkShortcut.Visibility = linked ? Visibility.Collapsed : Visibility.Visible;
        Empty.Visibility = Files.Items.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    private void ShowVehicles(object sender, RoutedEventArgs e) => Pages.SelectedItem = Vehicles;
    private void SelectVersion(object sender, SelectionChangedEventArgs e)
        => SaveVersion.IsEnabled = Versions.SelectedItem != null;

    private void PreparingTransfer(string message)
    {
        Progress.Value = 0;
        CancelButton.Visibility = Progress.Visibility = Visibility.Visible;
        Status.Text = message;
    }

    private static string DisplayError(Exception e)
    {
        string detail = e.Message;
        if (detail.Contains("FILE_TOO_LARGE")) return "파일이 너무 큽니다. 20 MiB 이하의 문서를 선택하세요.";
        if (detail.Contains("UNSUPPORTED") || detail.Contains("INVALID_DOCUMENT")) return "지원되는 TXT, PDF 또는 DOCX 문서를 선택하세요.";
        if (detail.Contains("RATE_LIMITED")) return "요청이 많습니다. 잠시 후 새 코드를 발급해 주세요.";
        if (detail.Contains("PAIRING") || detail.Contains("INVALID_INPUT")) return "코드가 유효한지 확인하고 다시 시도해 주세요.";
        if (detail.Contains("ALREADY_CONNECTED")) return "이미 연결된 차량이 있습니다. 차량 연결 화면을 확인하세요.";
        if (detail.Contains("AUTH") || detail.Contains("401") || detail.Contains("REPAIR_REQUIRED")) return "인증을 확인할 수 없습니다. 연결 상태를 다시 확인하세요.";
        if (detail.Contains("CONNECTION") || detail.Contains("404")) return "차량 연결 또는 파일을 찾을 수 없습니다. 연결을 확인하고 목록을 새로 고쳐 주세요.";
        if (detail.Contains("기존 파일")) return "기존 파일이 있습니다. 다른 이름으로 저장해 주세요.";
        if (detail.Contains("해시") || detail.Contains("INTEGRITY")) return "파일 내용이 일치하지 않습니다. 다시 전송해 주세요.";
        if (detail.Contains("복구할 전송")) return "다시 시도할 업로드가 없습니다.";
        if (detail.Contains("이전 전송") || detail.Contains("전송 상태") || detail.Contains("원래 서버")) return "원래 연결에서 업로드를 다시 시도하거나 재시도 기록을 정리하세요.";
        if (detail.Contains("먼저 등록") || detail.Contains("차량 연결이 필요")) return "차량 연결에서 연결 확인·복원을 먼저 눌러 주세요.";
        if (e is UriFormatException || detail.Contains("HTTPS") || detail.Contains("CONFIGURATION")) return "서비스 연결 설정이 준비되지 않았습니다. 설정 담당자에게 문의해 주세요.";
        return "연결 상태와 선택한 파일을 확인한 뒤 다시 시도해 주세요.";
    }

    private bool HasUsableSettings()
    {
        bool local = LocalMode.IsChecked == true;
        if (local && string.IsNullOrWhiteSpace(Server.Text))
            Server.Text = "http://127.0.0.1:3010/api/v1/realtime/";
        if (!Uri.TryCreate(Server.Text.Trim(), UriKind.Absolute, out var endpoint)
            || endpoint.UserInfo.Length != 0 || endpoint.Query.Length != 0 || endpoint.Fragment.Length != 0
            || endpoint.AbsolutePath.TrimEnd('/') != "/api/v1/realtime"
            || (endpoint.Scheme != "https" && !(endpoint.Scheme == "http" && endpoint.IsLoopback))
            || (local && (!endpoint.IsLoopback || Token.Password.Length == 0)))
        {
            Status.Text = "전송 서비스 설정이 필요합니다. 설정 담당자에게 문의해 주세요.";
            return false;
        }
        if (!local)
        {
            if (endpoint.Scheme != "https" || !Uri.TryCreate(AuthUrl.Text, UriKind.Absolute, out var auth) || auth.Scheme != "https"
                || auth.UserInfo.Length != 0 || auth.Query.Length != 0 || auth.Fragment.Length != 0 || auth.AbsolutePath != "/"
                || !IsPublishable(PublicKey.Text.Trim()))
            {
                Status.Text = "인증 서비스의 공개 설정이 필요합니다. 설정 담당자에게 문의해 주세요.";
                return false;
            }
        }
        // Never persist a privileged key typed into the optional developer field.
        if (local && PublicKey.Text.Length > 0 && !IsPublishable(PublicKey.Text.Trim()))
        {
            Status.Text = "공개 키만 입력할 수 있습니다.";
            return false;
        }
        return true;
    }

    private static bool IsPublishable(string value)
    {
        if (value.StartsWith("sb_publishable_", StringComparison.Ordinal)) return true;
        var fields = value.Split('.');
        if (fields.Length != 3) return false;
        try
        {
            var payload = fields[1].Replace('-', '+').Replace('_', '/');
            var json = JsonNode.Parse(Convert.FromBase64String(payload.PadRight((payload.Length + 3) / 4 * 4, '=')));
            return Field(json, "role") == "anon";
        }
        catch { return false; }
    }

    private async Task RefreshAfterUpload()
    {
        try { await LoadFiles(); }
        catch { Status.Text = "파일을 올렸습니다. 파일 목록을 다시 불러오려면 새로 고침을 누르세요."; }
    }

    private void ResetFileSelection()
    {
        Files.UnselectAll();
        Versions.UnselectAll();
        Versions.Items.Clear();
        RemoveFile.IsEnabled = SaveVersion.IsEnabled = false;
        VersionNote.Text = "파일을 선택하면 원본과 수정본을 확인할 수 있습니다.";
    }

    private async Task ShowFileDates()
    {
        // File records only carry the creation date. A READY version carries the actual commit date.
        foreach (Item row in Files.Items)
        {
            var result = await Api.Send("GET", $"files/{row.Id}/versions");
            var latest = result?["items"]?.AsArray().MaxBy(v => v?["versionNo"]?.GetValue<int>() ?? 0);
            row.ShowLatest(latest);
        }
    }
}
