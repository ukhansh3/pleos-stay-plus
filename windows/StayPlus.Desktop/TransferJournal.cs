using System.Text.Json.Nodes;
using System.Security.Cryptography;
namespace StayPlus.Desktop;
public static class TransferJournal {
 public static string Mime(string name)=>Path.GetExtension(name).ToLowerInvariant() switch {".txt"=>"text/plain",".pdf"=>"application/pdf",".docx"=>"application/vnd.openxmlformats-officedocument.wordprocessingml.document",_=>throw new InvalidOperationException("UNSUPPORTED_TYPE")};
 public static JsonNode? Load()=>JsonNode.Parse(TokenStore.ReadSecret("transfer-journal")??"null");
 public static void Save(JsonNode? job)=>TokenStore.WriteSecret("transfer-journal",job?.ToJsonString()??"null");
 public static async Task<JsonNode> Stage(string source,string connectionId,string server,CancellationToken ct,long maxBytes=20L*1024*1024){
  if(Load()!=null)throw new InvalidOperationException("이전 전송을 재시도하거나 명시적으로 취소하세요");
  var mime=Mime(source);var directory=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"StayPlus","staged");Directory.CreateDirectory(directory);var staged=Path.Combine(directory,Guid.NewGuid().ToString());
  try{await using(var input=File.OpenRead(source)){if(input.Length>maxBytes)throw new InvalidOperationException("FILE_TOO_LARGE");await using var output=new FileStream(staged,FileMode.CreateNew);await input.CopyToAsync(output,ct);}
   await using var verify=File.OpenRead(staged);var hash=Convert.ToHexString(await SHA256.HashDataAsync(verify,ct)).ToLowerInvariant();
   var job=new JsonObject{["server"]=server,["path"]=staged,["request"]=new JsonObject{["connectionId"]=connectionId,["fileName"]=Path.GetFileName(source),["mimeType"]=mime,["size"]=verify.Length,["sha256"]=hash,["idempotencyKey"]=Guid.NewGuid().ToString()}};Save(job);return job;
  }catch{File.Delete(staged);throw;}
 }
 public static void Discard(){var job=Load();Save(null);if(job!=null)File.Delete(job["path"]!.GetValue<string>());}
 public static async Task Execute(RealtimeClient api,string server,IProgress<double> progress,CancellationToken ct){
  var job=Load()??throw new InvalidOperationException("복구할 전송이 없습니다");if(job["server"]!.GetValue<string>()!=server)throw new InvalidOperationException("원래 서버 설정으로 복원하세요");
  var v=await api.Send("POST","uploads",job["request"],ct);var id=v!["id"]!.GetValue<string>();job["uploadId"]=id;Save(job);
  var state=v["state"]!.GetValue<string>();
  if(state=="PENDING"||state=="FAILED")await api.Put(id,job["path"]!.GetValue<string>(),progress,ct);
  else if(state!="VERIFYING"&&state!="READY")throw new InvalidOperationException($"전송 상태 {state}: 상태 조회 후 재시도/취소하세요");
  if(state!="READY")await api.Send("POST",$"uploads/{id}/complete",ct:ct);
  Save(null);File.Delete(job["path"]!.GetValue<string>());
 }
}
