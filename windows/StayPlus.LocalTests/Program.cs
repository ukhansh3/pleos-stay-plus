using System.Net;
using System.Text.Json.Nodes;
using System.Security.Cryptography;
using StayPlus.Desktop;
void Check(bool value,string message){if(!value)throw new Exception(message);}
string? saved=null;var fake=new FakeAuth();using(var auth=new IdentityProvider("https://fake.invalid","public",()=>saved,s=>saved=s,fake)){
 Check(await auth.ValidToken()=="access-1","signup");Check(await auth.ValidToken()=="access-1" && fake.Count==1,"cached token");
 var j=JsonNode.Parse(saved!)!;j["expires_at"]=0;saved=j.ToJsonString();Check(await auth.ValidToken()=="access-2"&&fake.LastPath.Contains("refresh_token"),"refresh");
 j=JsonNode.Parse(saved!)!;j["expires_at"]=0;saved=j.ToJsonString();fake.Status=HttpStatusCode.BadRequest;
 try{await auth.ValidToken();throw new Exception("accepted invalid refresh");}catch(InvalidOperationException e){Check(e.Message=="REPAIR_REQUIRED","permanent auth error");}
 fake.Status=HttpStatusCode.ServiceUnavailable;try{await auth.ValidToken();throw new Exception("accepted unavailable auth");}catch(InvalidOperationException e){Check(e.Message=="AUTH_UNAVAILABLE","transient auth error");}
 Check(fake.LastPath.Contains("refresh_token"),"must never silently sign up again");
}
Console.WriteLine("PASS Windows Fake Auth signup/cache/refresh/failure identity preservation");
if(args.Length<2)return;
using var pc=new RealtimeClient(args[0],"local-integration-pc-token-000000000000");using var car=new RealtimeClient(args[0],"local-integration-car-token-00000000000");
await pc.Send("POST","devices",new{kind="WINDOWS",name="Windows actual client"});await car.Send("POST","devices",new{kind="VEHICLE",name="Vehicle contract test peer"});
var pair=await pc.Send("POST","pairings");var pid=pair!["id"]!.GetValue<string>();await car.Send("POST","pairings/claim",new{code=pair["code"]!.GetValue<string>()});await pc.Send("POST",$"pairings/{pid}/approve");await car.Send("POST",$"pairings/{pid}/approve");
var current=await pc.Send("GET","connections/current");var cid=current!["connection"]!["id"]!.GetValue<string>();
var output=Path.Combine(Path.GetTempPath(),"stay-client-test-"+Guid.NewGuid());Directory.CreateDirectory(output);string? last=null;
try{
 foreach(var ext in new[]{"txt","pdf","docx"}){
  var path=Path.Combine(args[1],"document."+ext);var bytes=await File.ReadAllBytesAsync(path);var sha=Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();var mime=ext=="txt"?"text/plain":ext=="pdf"?"application/pdf":"application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  var input=new{connectionId=cid,fileName=Path.GetFileName(path),mimeType=mime,size=bytes.Length,sha256=sha,idempotencyKey=Guid.NewGuid().ToString()};
  var v=await pc.Send("POST","uploads",input);var id=v!["id"]!.GetValue<string>();await pc.Put(id,path,new Progress<double>(),default);await pc.Send("POST",$"uploads/{id}/complete");
  await car.Download(id,Path.Combine(output,"car."+ext),sha,new Progress<double>(),default);
  var revised=await car.Send("POST","uploads",new{connectionId=cid,fileId=v["fileId"]!.GetValue<string>(),baseVersionId=id,fileName="revision."+ext,mimeType=mime,size=bytes.Length,sha256=sha,idempotencyKey=Guid.NewGuid().ToString()});
  var rid=revised!["id"]!.GetValue<string>();await car.Put(rid,path,new Progress<double>(),default);var done=await car.Send("POST",$"uploads/{rid}/complete");Check(done!["versionNo"]!.GetValue<int>()==2,"version sequence");
  await pc.Download(rid,Path.Combine(output,"pc."+ext),sha,new Progress<double>(),default);Check((await pc.Send("POST","uploads",input))!["id"]!.GetValue<string>()==id,"idempotency");
  await pc.Download(id,Path.Combine(output,"original."+ext),sha,new Progress<double>(),default);last=id;
  Console.WriteLine($"PASS actual C# HTTP client {ext} original/revision/hash/idempotency");
 }
 var exportPath = Path.Combine(args[1], "document.docx");
 var exportBytes = await File.ReadAllBytesAsync(exportPath);
 var exportSha = Convert.ToHexString(SHA256.HashData(exportBytes)).ToLowerInvariant();
 var exportRequest = new {
  connectionId = cid, fileName = "vehicle-export.docx",
  mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  size = exportBytes.Length, sha256 = exportSha, idempotencyKey = Guid.NewGuid().ToString()
 };
 var export = await car.Send("POST", "uploads", exportRequest);
 var exportId = export!["id"]!.GetValue<string>();
 var exportFileId = export["fileId"]!.GetValue<string>();
 await car.Put(exportId, exportPath, new Progress<double>(), default);
 var ready = await car.Send("POST", $"uploads/{exportId}/complete");
 Check(ready!["kind"]!.GetValue<string>() == "ORIGINAL" && ready["versionNo"]!.GetValue<int>() == 1,
  "vehicle export must become an original");
 Check((await car.Send("POST", "uploads", exportRequest))!["id"]!.GetValue<string>() == exportId,
  "retry must reuse original vehicle upload");
 var files = await pc.Send("GET", "files");
 Check(files!["items"]!.AsArray().Any(file => file!["id"]!.GetValue<string>() == exportFileId),
  "PC must list vehicle export");
 var versions = await pc.Send("GET", $"files/{exportFileId}/versions");
 Check(versions!["items"]!.AsArray().Single()!["id"]!.GetValue<string>() == exportId,
  "PC must see exactly one original version");
 var downloaded = Path.Combine(output, "vehicle-export.docx");
 await pc.Download(exportId, downloaded, exportSha, new Progress<double>(), default);
 Check((await File.ReadAllBytesAsync(downloaded)).SequenceEqual(exportBytes), "vehicle export bytes changed");
 using (var docx = System.IO.Compression.ZipFile.OpenRead(downloaded)) {
  using var xml = docx.GetEntry("word/document.xml")!.Open();
  Check(System.Xml.Linq.XDocument.Load(xml).Root?.Name.LocalName == "document", "DOCX must reopen");
 }
 Console.WriteLine("PASS vehicle original DOCX, actual C# PC list/download/hash/XML reopen");
 await pc.Send("DELETE",$"connections/{cid}");try{await car.Download(last!,Path.Combine(output,"denied"),"",new Progress<double>(),default);throw new Exception("revocation bypass");}catch(InvalidOperationException e){Check(e.Message=="NO_CONNECTION","revocation status");}
 Console.WriteLine("PASS revoked connection denied");
}finally{Directory.Delete(output,true);}
sealed class FakeAuth:HttpMessageHandler{
 public int Count;public string LastPath="";public HttpStatusCode Status=HttpStatusCode.OK;
 protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken ct){Count++;LastPath=request.RequestUri!.ToString();return Task.FromResult(new HttpResponseMessage(Status){Content=new StringContent(new JsonObject{["access_token"]="access-"+Count,["refresh_token"]="refresh",["expires_in"]=3600,["user"]=new JsonObject{["id"]="stable-uid"}}.ToJsonString())});}
}
