using System.Net.Http;
using System.Net.Http.Json;
using System.Text.Json.Nodes;
namespace StayPlus.Desktop;
/// <summary>Supabase authentication boundary, independent of Pickup. Not exercised against Cloud.</summary>
public sealed class IdentityProvider(string url,string publishableKey,Func<string?> read,Action<string> write,HttpMessageHandler? handler=null) : IDisposable {
 readonly SemaphoreSlim mutex=new(1,1); readonly HttpClient http=new(handler ?? new HttpClientHandler{AllowAutoRedirect=false}){Timeout=TimeSpan.FromSeconds(15)};
 public async Task<string> ValidToken(CancellationToken ct=default){
  await mutex.WaitAsync(ct);
  try{
   if(new Uri(url).Scheme!="https")throw new InvalidOperationException("HTTPS_REQUIRED");
   var saved=read();var current=saved==null?null:JsonNode.Parse(saved);
   var now=DateTimeOffset.UtcNow.ToUnixTimeSeconds();
   if(current!=null && current["expires_at"]!.GetValue<long>()>now+60)return current["access_token"]!.GetValue<string>();
   var path=current==null?"signup":"token?grant_type=refresh_token";
   using var request=new HttpRequestMessage(HttpMethod.Post,url.TrimEnd('/')+"/auth/v1/"+path);
   request.Headers.Add("apikey",publishableKey);
   request.Content=JsonContent.Create(current==null?new JsonObject():new JsonObject{["refresh_token"]=current["refresh_token"]!.GetValue<string>()});
   using var response=await http.SendAsync(request,ct);
   if(!response.IsSuccessStatusCode)throw new InvalidOperationException((int)response.StatusCode>=500 || (int)response.StatusCode==429?"AUTH_UNAVAILABLE":current==null?"AUTH_BLOCKED":"REPAIR_REQUIRED");
   var session=JsonNode.Parse(await response.Content.ReadAsStringAsync(ct))!;
   session["expires_at"]=now+session["expires_in"]!.GetValue<long>();
   if(current!=null && current["user"]!["id"]!.GetValue<string>()!=session["user"]!["id"]!.GetValue<string>())throw new InvalidOperationException("IDENTITY_CHANGED");
   write(session.ToJsonString());return session["access_token"]!.GetValue<string>();
  }finally{mutex.Release();}
 }
 public void Dispose(){http.Dispose();mutex.Dispose();}
}
