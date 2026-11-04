using System.Windows;
using System.Text.Json.Nodes;
using Microsoft.Win32;
using System.Security.Cryptography;
namespace StayPlus.Desktop;
public partial class MainWindow : Window {
 IdentityProvider? identity; RealtimeClient? api; string? pairingId,connectionId; string connectedServer=""; CancellationTokenSource? transfer; bool busy;
 record Item(string Id,string Label,JsonNode Data);
 public MainWindow(){InitializeComponent();try{var saved=TokenStore.Load();if(saved is {Length:2}){Server.Text=saved[0];Token.Password=saved[1];}var config=TokenStore.ReadSecret("connection-config");if(config!=null){var j=JsonNode.Parse(config)!;Server.Text=j["server"]!.GetValue<string>();AuthUrl.Text=j["authUrl"]!.GetValue<string>();PublicKey.Text=j["publicKey"]!.GetValue<string>();LocalMode.IsChecked=j["local"]!.GetValue<bool>();}}catch{Status.Text="저장된 로컬 인증을 복원할 수 없습니다. 다시 입력하세요.";}Closed+=(_,_)=>{transfer?.Cancel();api?.Dispose();};}
 async Task Run(Func<Task> action){if(busy)return;busy=true;try{await action();}catch(OperationCanceledException){Status.Text="취소됨. 목록/전송 상태를 확인한 뒤 재시도하세요.";}catch(Exception e){Status.Text=e.Message;}finally{busy=false;}}
 RealtimeClient Api=>api??throw new InvalidOperationException("먼저 등록하세요");
 async Task Restore(){var result=await Api.Send("GET","connections/current"); connectionId=result?["connection"]?["id"]?.GetValue<string>();Connection.Text=result?.ToJsonString();}
 async void Connect(object sender,RoutedEventArgs e)=>await Run(async()=>{api?.Dispose();identity?.Dispose();if(LocalMode.IsChecked==true){if(!new Uri(Server.Text).IsLoopback)throw new InvalidOperationException("LOCAL_MODE_REQUIRES_LOOPBACK");api=new RealtimeClient(Server.Text,Token.Password);}else{var scope=Convert.ToHexString(SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(AuthUrl.Text))).ToLowerInvariant();identity=new IdentityProvider(AuthUrl.Text,PublicKey.Text,()=>TokenStore.ReadSecret("auth-"+scope),value=>TokenStore.WriteSecret("auth-"+scope,value));api=new RealtimeClient(Server.Text,identity.ValidToken);}await Api.Send("POST","devices",new{kind="WINDOWS",name=Environment.MachineName});await Restore();connectedServer=Server.Text;TokenStore.Save(Server.Text,LocalMode.IsChecked==true?Token.Password:"");TokenStore.WriteSecret("connection-config",new JsonObject{["server"]=Server.Text,["authUrl"]=AuthUrl.Text,["publicKey"]=PublicKey.Text,["local"]=LocalMode.IsChecked==true}.ToJsonString());Status.Text="기기 등록 완료 · 인증 정보 DPAPI 보관";});
 async void Issue(object sender,RoutedEventArgs e)=>await Run(async()=>{var p=await Api.Send("POST","pairings");pairingId=p!["id"]!.GetValue<string>();Code.Text=p["code"]!.GetValue<string>();Status.Text="차량에서 코드를 입력한 후 상대 확인 및 양쪽 승인을 진행하세요.";});
 async void Claim(object sender,RoutedEventArgs e)=>await Run(async()=>{var p=await Api.Send("POST","pairings/claim",new{code=Code.Text});pairingId=p!["id"]!.GetValue<string>();Connection.Text=p.ToJsonString();});
 async void PairStatus(object sender,RoutedEventArgs e)=>await Run(async()=>{await Restore();Connection.Text=(await Api.Send("GET",$"pairings/{pairingId}"))?.ToJsonString();});
 async void Approve(object sender,RoutedEventArgs e)=>await Run(async()=>{var p=await Api.Send("GET",$"pairings/{pairingId}");if(MessageBox.Show(p?.ToJsonString(),"상대 기기를 확인하고 승인",MessageBoxButton.OKCancel)!=MessageBoxResult.OK)return;await Api.Send("POST",$"pairings/{pairingId}/approve");await Restore();});
 async void Disconnect(object sender,RoutedEventArgs e)=>await Run(async()=>{if(connectionId!=null)await Api.Send("DELETE",$"connections/{connectionId}");Files.Items.Clear();Versions.Items.Clear();await Restore();});
 async Task LoadFiles(){var result=await Api.Send("GET","files");Files.Items.Clear();foreach(var f in result!["items"]!.AsArray())Files.Items.Add(new Item(f!["id"]!.GetValue<string>(),f["name"]!.GetValue<string>(),f));}
 async void Refresh(object sender,RoutedEventArgs e)=>await Run(LoadFiles);
 async void SelectFile(object sender,System.Windows.Controls.SelectionChangedEventArgs e){if(Files.SelectedItem is not Item f)return;await Run(async()=>{Versions.Items.Clear();var result=await Api.Send("GET",$"files/{f.Id}/versions");foreach(var v in result!["items"]!.AsArray())Versions.Items.Add(new Item(v!["id"]!.GetValue<string>(),$"v{v["versionNo"]} · {v["fileName"]} · {v["size"]} bytes",v));});}
 async void Upload(object sender,RoutedEventArgs e)=>await Run(async()=>{
  if(connectionId==null)throw new InvalidOperationException("차량 연결이 필요합니다");var dialog=new OpenFileDialog{Filter="문서|*.txt;*.pdf;*.docx"};if(dialog.ShowDialog()!=true)return;
  transfer=new();await TransferJournal.Stage(dialog.FileName,connectionId,connectedServer,transfer.Token);
  await TransferJournal.Execute(Api,connectedServer,new Progress<double>(v=>Progress.Value=v),transfer.Token);Status.Text="업로드 확정 완료";await LoadFiles(); });
 async void Download(object sender,RoutedEventArgs e)=>await Run(async()=>{if(Versions.SelectedItem is not Item v)return;var dialog=new SaveFileDialog{FileName=$"v{v.Data["versionNo"]}_{v.Data["fileName"]}",OverwritePrompt=true};if(dialog.ShowDialog()!=true)return;if(File.Exists(dialog.FileName))throw new InvalidOperationException("기존 파일을 보존하려면 새 이름을 선택하세요");transfer=new();await Api.Download(v.Id,dialog.FileName,v.Data["sha256"]!.GetValue<string>(),new Progress<double>(x=>Progress.Value=x),transfer.Token);Status.Text="다운로드·해시 검증 완료";});
 async void DeleteFile(object sender,RoutedEventArgs e)=>await Run(async()=>{if(Files.SelectedItem is Item f && MessageBox.Show("모든 버전을 삭제할까요?","문서 삭제",MessageBoxButton.YesNo)==MessageBoxResult.Yes){await Api.Send("DELETE",$"files/{f.Id}");Versions.Items.Clear();await LoadFiles();}});
 async void RetryUpload(object sender,RoutedEventArgs e)=>await Run(async()=>{transfer=new();await TransferJournal.Execute(Api,connectedServer,new Progress<double>(v=>Progress.Value=v),transfer.Token);Status.Text="복구 완료";await LoadFiles();});
 void DiscardUpload(object sender,RoutedEventArgs e){if(busy)return;if(MessageBox.Show("미완료 전송의 로컬 복구 기록을 지울까요? 서버의 미확정 파일은 만료 후 정리됩니다.","전송 포기",MessageBoxButton.YesNo)==MessageBoxResult.Yes)TransferJournal.Discard();}
 void Cancel(object sender,RoutedEventArgs e)=>transfer?.Cancel();
}



