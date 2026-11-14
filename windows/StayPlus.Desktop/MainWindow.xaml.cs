using System.Windows;
using System.Text.Json.Nodes;
using Microsoft.Win32;
using System.Security.Cryptography;
using System.Reflection;
using System.Windows.Threading;
namespace StayPlus.Desktop;

public partial class MainWindow : Window
{
    IdentityProvider? identity; RealtimeClient? api; string? pairingId, connectionId; string connectedServer = ""; CancellationTokenSource? transfer; bool busy;

    readonly DispatcherTimer pairingPoll = new() { Interval = TimeSpan.FromSeconds(3) };
    bool checkingPairing;
    bool closing;

    public MainWindow()
    {
        InitializeComponent();
        SetDisplayDefaults();
        Pages.SelectedItem = Vehicles;
        DeveloperConfiguration.Visibility = IsDeveloperBuild ? Visibility.Visible : Visibility.Collapsed;
        try { var saved = TokenStore.Load(); if (saved is { Length: 2 }) { Server.Text = saved[0]; Token.Password = saved[1]; LocalMode.IsChecked = saved[1].Length > 0; } var config = TokenStore.ReadSecret("connection-config"); if (config != null) { var j = JsonNode.Parse(config)!; Server.Text = j["server"]!.GetValue<string>(); AuthUrl.Text = j["authUrl"]!.GetValue<string>(); PublicKey.Text = j["publicKey"]!.GetValue<string>(); LocalMode.IsChecked = j["local"]!.GetValue<bool>(); } } catch { Status.Text = "저장한 연결 설정을 읽을 수 없습니다. 설정 담당자에게 문의해 주세요."; }
        pairingPoll.Tick += async (_, _) => await ObservePairing();
        pairingPoll.Start();
        Closed += (_, _) => { closing = true; pairingPoll.Stop(); transfer?.Cancel(); api?.Dispose(); identity?.Dispose(); };
    }

    private static string BuildSetting(string key) => typeof(MainWindow).Assembly
        .GetCustomAttributes<AssemblyMetadataAttribute>().FirstOrDefault(value => value.Key == key)?.Value ?? "";
    async Task Run(Func<Task> action) { if (busy) return; busy = true; Pages.IsEnabled = false; Status.Foreground = System.Windows.Media.Brushes.DarkSlateGray; try { await action(); } catch (OperationCanceledException) { Status.Text = "취소됨. 목록/전송 상태를 확인한 뒤 재시도하세요."; } catch (Exception e) { Status.Foreground = System.Windows.Media.Brushes.DarkRed; Status.Text = (transfer != null ? "전송 실패 · " : "") + DisplayError(e); } finally { busy = false; Pages.IsEnabled = true; transfer?.Dispose(); transfer = null; Progress.Visibility = CancelButton.Visibility = Visibility.Collapsed; } }
    RealtimeClient Api => api ?? throw new InvalidOperationException("먼저 등록하세요");
    async Task Restore(CancellationToken ct = default) { var result = await Api.Send("GET", "connections/current", ct: ct); var previous = connectionId; connectionId = result?["connection"]?["id"]?.GetValue<string>(); if (previous != connectionId) { Files.Items.Clear(); ResetFileSelection(); } DisplayConnection(result?["connection"]); }
    async void Connect(object sender, RoutedEventArgs e) => await Run(async () =>
    {
        if (await ConnectService()) Status.Text = connectionId == null
            ? "연결할 준비가 됐습니다. 차량의 8자리 코드를 입력하세요."
            : "차량 연결을 복원했습니다.";
    });

    private async Task<bool> ConnectService()
    {
        if (!HasUsableSettings()) return false;
        Status.Text = "연결 준비 중…";
        Files.Items.Clear();
        ResetFileSelection();
        connectionId = pairingId = vehicleId = vehicleName = null;
        ApproveVehicle.IsEnabled = false;
        PairNote.Text = "상대 기기를 확인한 후 승인하세요.";
        DisplayConnection(null);
        api?.Dispose();
        identity?.Dispose();
        api = null;
        identity = null;
        bool local = LocalMode.IsChecked == true;
        string server = Server.Text.Trim().TrimEnd('/') + "/";
        if (local)
            api = new RealtimeClient(server, Token.Password);
        else
        {
            var scope = Convert.ToHexString(SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(AuthUrl.Text))).ToLowerInvariant();
            identity = new IdentityProvider(AuthUrl.Text, PublicKey.Text.Trim(), () => TokenStore.ReadSecret("auth-" + scope),
                value => TokenStore.WriteSecret("auth-" + scope, value));
            api = new RealtimeClient(server, identity.ValidToken);
        }
        try
        {
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(20));
            await Api.Send("POST", "devices", new { kind = "WINDOWS", name = Environment.MachineName }, timeout.Token);
            await Restore(timeout.Token);
        }
        catch
        {
            api?.Dispose();
            identity?.Dispose();
            api = null;
            identity = null;
            throw;
        }
        connectedServer = server;
        TokenStore.Save(server, local ? Token.Password : "");
        TokenStore.WriteSecret("connection-config", new JsonObject { ["server"] = server, ["authUrl"] = AuthUrl.Text,
            ["publicKey"] = PublicKey.Text.Trim(), ["local"] = local }.ToJsonString());
        if (connectionId != null) await LoadFiles();
        return true;
    }
    async void Issue(object sender, RoutedEventArgs e) => await Run(async () => { if (api == null && !await ConnectService()) return; var p = await Api.Send("POST", "pairings"); pairingId = p!["id"]!.GetValue<string>(); Code.Text = p["code"]!.GetValue<string>(); PairNote.Text = "차량에 이 코드를 입력한 뒤 상대 확인을 누르세요."; Status.Text = "차량에서 코드를 입력한 후 상대 확인 및 양쪽 승인을 진행하세요."; });
    async void Claim(object sender, RoutedEventArgs e) => await Run(async () =>
    {
        var code = Code.Text.Trim();
        if (code.Length != 8 || code.Any(c => c is < '0' or > '9'))
        {
            Status.Text = "차량에 표시된 8자리 숫자를 입력하세요.";
            return;
        }
        if (api == null && !await ConnectService()) return;
        var pair = await Api.Send("POST", "pairings/claim", new { code });
        pairingId = pair!["id"]!.GetValue<string>();
        Code.Clear();
        PairNote.Text = DescribePair(pair);
        Status.Text = PairNote.Text;
    });

    private async Task ObservePairing()
    {
        if (closing || busy || checkingPairing || connectionId != null || pairingId is not string id || api is not RealtimeClient client) return;
        checkingPairing = true;
        try
        {
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
            var pair = await client.Send("GET", $"pairings/{id}", ct: timeout.Token);
            if (closing || busy || pairingId != id || api != client) return;
            PairNote.Text = DescribePair(pair);
            var phase = Field(pair, "state");
            if (phase == "CONNECTED") await Run(async () =>
            {
                await Restore();
                if (connectionId != null) { Status.Text = "차량 연결이 완료되었습니다."; await LoadFiles(); }
            });
            if (phase is "CANCELLED" or "EXPIRED") pairingId = null;
        }
        catch
        {
            if (!closing && !busy && pairingId == id && api == client)
                PairNote.Text = "연결 요청을 확인할 수 없습니다. 네트워크를 확인해 주세요.";
        }
        finally { checkingPairing = false; }
    }
    async void PairStatus(object sender, RoutedEventArgs e) => await Run(async () => { await Restore(); if (pairingId == null) { Status.Text = "먼저 연결 코드를 발급하거나 입력하세요."; return; } PairNote.Text = DescribePair(await Api.Send("GET", $"pairings/{pairingId}")); Status.Text = PairNote.Text; });
    async void Approve(object sender, RoutedEventArgs e) => await Run(async () =>
    {
        if (pairingId == null) { Status.Text = "먼저 연결 코드를 발급하거나 입력하세요."; return; }
        var p = await Api.Send("GET", $"pairings/{pairingId}");
        PairNote.Text = DescribePair(p);
        if (!PairingApproval.CanApprove(p))
        {
            Status.Text = PairNote.Text;
            if (Field(p, "state") == "CONNECTED") { await Restore(); if (connectionId != null) await LoadFiles(); }
            return;
        }
        if (MessageBox.Show(this, $"‘{vehicleName}’ 차량과 이 PC를 연결할까요?", "차량 연결 승인", MessageBoxButton.OKCancel, MessageBoxImage.Question, MessageBoxResult.Cancel) != MessageBoxResult.OK) return;
        PairNote.Text = DescribePair(await Api.Send("POST", $"pairings/{pairingId}/approve"));
        await Restore();
        Status.Text = connectionId != null ? "차량과 연결했습니다." : "PC에서 승인했습니다. 차량에서도 이 PC를 승인해 주세요.";
        if (connectionId != null) await LoadFiles();
    });
    async void Disconnect(object sender, RoutedEventArgs e) => await Run(async () => { if (connectionId != null) await Api.Send("DELETE", $"connections/{connectionId}"); Files.Items.Clear(); ResetFileSelection(); await Restore(); Code.Clear(); pairingId = null; ApproveVehicle.IsEnabled = false; PairNote.Text = "새 차량과 연결하려면 차량의 코드를 입력하세요."; Status.Text = "차량 연결을 해제했습니다."; });
    async Task LoadFiles() { var result = await Api.Send("GET", "files"); Files.Items.Clear(); ResetFileSelection(); foreach (var f in result!["items"]!.AsArray()) Files.Items.Add(new Item(f!["id"]!.GetValue<string>(), f["name"]!.GetValue<string>(), f)); Empty.Visibility = Files.Items.Count == 0 ? Visibility.Visible : Visibility.Collapsed; await ShowFileDates(); }
    async void Refresh(object sender, RoutedEventArgs e) => await Run(async () => { await Restore(); await LoadFiles(); Status.Text = "파일 목록을 새로 고쳤습니다."; });
    async void SelectFile(object sender, System.Windows.Controls.SelectionChangedEventArgs e) { RemoveFile.IsEnabled = Files.SelectedItem != null; if (Files.SelectedItem is not Item f) { VersionNote.Text = "파일을 선택하면 원본과 수정본을 확인할 수 있습니다."; return; } VersionNote.Text = f.Name + " · 원본은 보관되며 수정본은 별도 버전으로 저장됩니다."; await Run(async () => { Versions.Items.Clear(); var result = await Api.Send("GET", $"files/{f.Id}/versions"); foreach (var v in result!["items"]!.AsArray()) Versions.Items.Add(new Item(v!["id"]!.GetValue<string>(), VersionLabel(v), v)); }); }
    async void Upload(object sender, RoutedEventArgs e) => await Run(async () =>
    {
        if (connectionId == null) { Pages.SelectedItem = Vehicles; Status.Text = "파일을 보내려면 차량 연결을 확인하세요."; return; }
        var dialog = new OpenFileDialog { Filter = "문서|*.txt;*.pdf;*.docx" }; if (dialog.ShowDialog() != true) return;
        PreparingTransfer("파일을 올리는 중입니다."); transfer = new(); await TransferJournal.Stage(dialog.FileName, connectionId, connectedServer, transfer.Token);
        await TransferJournal.Execute(Api, connectedServer, new Progress<double>(v => Progress.Value = v), transfer.Token); Status.Text = "파일을 올렸습니다."; await RefreshAfterUpload();
    });
    async void Download(object sender, RoutedEventArgs e) => await Run(async () => { if (Versions.SelectedItem is not Item v) return; var dialog = new SaveFileDialog { FileName = $"v{v.Data["versionNo"]}_{v.Data["fileName"]}", OverwritePrompt = true }; if (dialog.ShowDialog() != true) return; if (File.Exists(dialog.FileName)) throw new InvalidOperationException("기존 파일을 보존하려면 새 이름을 선택하세요"); PreparingTransfer("파일을 내려받는 중입니다."); transfer = new(); await Api.Download(v.Id, dialog.FileName, v.Data["sha256"]!.GetValue<string>(), new Progress<double>(x => Progress.Value = x), transfer.Token); Status.Text = "다운로드를 완료했습니다. 파일 내용이 일치합니다."; });
    async void DeleteFile(object sender, RoutedEventArgs e) => await Run(async () => { if (Files.SelectedItem is Item f && MessageBox.Show(this, $"‘{f.Name}’ 파일의 원본과 모든 수정본을 삭제할까요?", "파일 삭제", MessageBoxButton.YesNo, MessageBoxImage.Warning, MessageBoxResult.No) == MessageBoxResult.Yes) { await Api.Send("DELETE", $"files/{f.Id}"); Versions.Items.Clear(); await LoadFiles(); Status.Text = "파일과 버전을 삭제했습니다."; } });
    async void RetryUpload(object sender, RoutedEventArgs e) => await Run(async () => { PreparingTransfer("중단된 업로드를 다시 시도합니다."); transfer = new(); await TransferJournal.Execute(Api, connectedServer, new Progress<double>(v => Progress.Value = v), transfer.Token); Status.Text = "업로드를 완료했습니다."; await RefreshAfterUpload(); });
    void DiscardUpload(object sender, RoutedEventArgs e) { if (busy) return; if (MessageBox.Show("미완료 전송의 로컬 복구 기록을 지울까요? 서버의 미확정 파일은 만료 후 정리됩니다.", "전송 포기", MessageBoxButton.YesNo) == MessageBoxResult.Yes) TransferJournal.Discard(); }
    void Cancel(object sender, RoutedEventArgs e) => transfer?.Cancel();
}
