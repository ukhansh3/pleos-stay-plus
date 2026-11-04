using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json.Nodes;
namespace StayPlus.Desktop;
public sealed class RealtimeClient : IDisposable {
 readonly HttpClient http = new() { Timeout = TimeSpan.FromMinutes(10) };
 readonly Func<CancellationToken,Task<string>> tokenProvider;
 public RealtimeClient(string url,string token):this(url,_=>Task.FromResult(token)) {}
 public RealtimeClient(string url, Func<CancellationToken,Task<string>> provider) {
  var uri = new Uri(url);
  if (uri.Scheme != "https" && !(uri.Scheme == "http" && uri.IsLoopback)) throw new InvalidOperationException("HTTPS 또는 로컬 loopback만 허용합니다");
  http.BaseAddress = uri; tokenProvider=provider;
 }
 public async Task<JsonNode?> Send(string method,string path,object? body=null,CancellationToken ct=default) {
  using var request = new HttpRequestMessage(new HttpMethod(method),path);
  request.Headers.Authorization=new AuthenticationHeaderValue("Bearer",await tokenProvider(ct));
  if(body!=null) request.Content=JsonContent.Create(body);
  using var response=await http.SendAsync(request,ct);
  var text=await response.Content.ReadAsStringAsync(ct);
  if(!response.IsSuccessStatusCode) throw new InvalidOperationException($"{(int)response.StatusCode}: {text}");
  return string.IsNullOrWhiteSpace(text)?null:JsonNode.Parse(text);
 }
 public async Task Put(string id,string path,IProgress<double> progress,CancellationToken ct) {
  using var stream=File.OpenRead(path); using var content=new ProgressContent(stream,progress); content.SetType();
  using var request=new HttpRequestMessage(HttpMethod.Put,$"uploads/{id}/content"){Content=content};
  request.Headers.Authorization=new AuthenticationHeaderValue("Bearer",await tokenProvider(ct));
  using var response=await http.SendAsync(request,ct);
  if(!response.IsSuccessStatusCode) throw new InvalidOperationException(await response.Content.ReadAsStringAsync(ct));
 }
 public async Task Download(string id,string path,string expected,IProgress<double> progress,CancellationToken ct) {
  var connectionBefore=(await Send("GET","connections/current",ct:ct))?["connection"]?["id"]?.GetValue<string>() ?? throw new InvalidOperationException("NO_CONNECTION");
  var temporary=path+"."+Guid.NewGuid()+".part";
  try {
   using var request=new HttpRequestMessage(HttpMethod.Get,$"versions/{id}/content");
   request.Headers.Authorization=new AuthenticationHeaderValue("Bearer",await tokenProvider(ct));
   using var response=await http.SendAsync(request,HttpCompletionOption.ResponseHeadersRead,ct); response.EnsureSuccessStatusCode();
   await using(var source=await response.Content.ReadAsStreamAsync(ct))
   await using(var output=new FileStream(temporary,FileMode.CreateNew,FileAccess.Write,FileShare.None,65536,true)) {
    var buffer=new byte[65536]; long total=0; int count;
    while((count=await source.ReadAsync(buffer,ct))>0) { await output.WriteAsync(buffer.AsMemory(0,count),ct); total+=count; progress.Report(response.Content.Headers.ContentLength is >0 ? total*100.0/response.Content.Headers.ContentLength.Value : 0); }
   }
   await using var verify=File.OpenRead(temporary);
   var hash=Convert.ToHexString(await System.Security.Cryptography.SHA256.HashDataAsync(verify,ct)).ToLowerInvariant();
   if(hash!=expected) throw new InvalidOperationException("다운로드 해시 불일치");
   verify.Close(); ct.ThrowIfCancellationRequested();if((await Send("GET","connections/current",ct:ct))?["connection"]?["id"]?.GetValue<string>()!=connectionBefore)throw new InvalidOperationException("CONNECTION_REVOKED"); File.Move(temporary,path,false);
  } finally { if(File.Exists(temporary)) File.Delete(temporary); }
 }
 public void Dispose()=>http.Dispose();
 sealed class ProgressContent(Stream stream,IProgress<double> progress):HttpContent {
  protected override bool TryComputeLength(out long length){ length=stream.Length; return true; }
  protected override Task SerializeToStreamAsync(Stream output,System.Net.TransportContext? context)=>Copy(output,CancellationToken.None);
  protected override Task SerializeToStreamAsync(Stream output,System.Net.TransportContext? context,CancellationToken ct)=>Copy(output,ct);
  async Task Copy(Stream output,CancellationToken ct){ Headers.ContentType=new MediaTypeHeaderValue("application/octet-stream"); var buffer=new byte[65536];long total=0;int n;while((n=await stream.ReadAsync(buffer,ct))>0){await output.WriteAsync(buffer.AsMemory(0,n),ct);total+=n;progress.Report(total*100.0/stream.Length);} }
  public void SetType()=>Headers.ContentType=new MediaTypeHeaderValue("application/octet-stream");
 }
}


