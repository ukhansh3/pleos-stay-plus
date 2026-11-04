using System.Runtime.InteropServices;
using System.Text;
namespace StayPlus.Desktop;
public static class TokenStore {
 [StructLayout(LayoutKind.Sequential)] struct Blob { public int Length; public IntPtr Data; }
 [DllImport("crypt32.dll",SetLastError=true,CharSet=CharSet.Unicode)] static extern bool CryptProtectData(ref Blob input,string? description,IntPtr entropy,IntPtr reserved,IntPtr prompt,int flags,out Blob output);
 [DllImport("crypt32.dll",SetLastError=true,CharSet=CharSet.Unicode)] static extern bool CryptUnprotectData(ref Blob input,IntPtr description,IntPtr entropy,IntPtr reserved,IntPtr prompt,int flags,out Blob output);
 [DllImport("kernel32.dll")] static extern IntPtr LocalFree(IntPtr memory);
 static readonly string PathName=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"StayPlus","local-credential.dpapi");
 static byte[] Transform(byte[] bytes,bool encrypt){var input=new Blob{Length=bytes.Length,Data=Marshal.AllocHGlobal(bytes.Length)};Blob output=default;try{Marshal.Copy(bytes,0,input.Data,bytes.Length);bool ok=encrypt?CryptProtectData(ref input,null,IntPtr.Zero,IntPtr.Zero,IntPtr.Zero,1,out output):CryptUnprotectData(ref input,IntPtr.Zero,IntPtr.Zero,IntPtr.Zero,IntPtr.Zero,1,out output);if(!ok)throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());var result=new byte[output.Length];Marshal.Copy(output.Data,result,0,result.Length);return result;}finally{Marshal.FreeHGlobal(input.Data);if(output.Data!=IntPtr.Zero)LocalFree(output.Data);}}
 public static void WriteSecret(string name,string value){if(!System.Text.RegularExpressions.Regex.IsMatch(name,"^[a-zA-Z0-9-]+$"))throw new ArgumentException("INVALID_SECRET_NAME");Directory.CreateDirectory(Path.GetDirectoryName(PathName)!);var path=Path.Combine(Path.GetDirectoryName(PathName)!,name+".dpapi");var temp=path+".tmp";File.WriteAllBytes(temp,Transform(Encoding.UTF8.GetBytes(value),true));File.Move(temp,path,true);}
 public static string? ReadSecret(string name){if(!System.Text.RegularExpressions.Regex.IsMatch(name,"^[a-zA-Z0-9-]+$"))throw new ArgumentException("INVALID_SECRET_NAME");var path=Path.Combine(Path.GetDirectoryName(PathName)!,name+".dpapi");return File.Exists(path)?Encoding.UTF8.GetString(Transform(File.ReadAllBytes(path),false)):null;}
 public static void Save(string url,string token){Directory.CreateDirectory(Path.GetDirectoryName(PathName)!);var bytes=Transform(Encoding.UTF8.GetBytes(System.Text.Json.JsonSerializer.Serialize(new[]{url,token})),true);var temp=PathName+".tmp";File.WriteAllBytes(temp,bytes);File.Move(temp,PathName,true);}
 public static string[]? Load(){if(!File.Exists(PathName))return null;return System.Text.Json.JsonSerializer.Deserialize<string[]>(Encoding.UTF8.GetString(Transform(File.ReadAllBytes(PathName),false)));}
}

