import { mkdtemp, open, rm } from "node:fs/promises";
import { createWriteStream, createReadStream } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pipeline } from "node:stream/promises";
import { Readable, Transform } from "node:stream";
import { inflateRawSync } from "node:zlib";
import { requireCondition } from "./domain.js";
export const DOCUMENT_TYPES: Readonly<Record<string,string>> = { txt:"text/plain",pdf:"application/pdf",docx:"application/vnd.openxmlformats-officedocument.wordprocessingml.document" };
export function validateMetadata(name:string,mime:string){const extension=name.split(".").pop()?.toLowerCase() ?? "";requireCondition(DOCUMENT_TYPES[extension]===mime,415,"UNSUPPORTED_TYPE");return extension;}
/** Structural screening, not a malware scanner or document renderer. No full-file buffering. */
export async function validateDocument(source:Readable,name:string,mime:string,maxBytes:number){
 const extension=validateMetadata(name,mime);const root=await mkdtemp(join(tmpdir(),"stay-validation-"));const path=join(root,"content");let count=0;
 try{
  await pipeline(source,new Transform({transform(chunk:Buffer,_encoding,done){count+=chunk.length;done(count>maxBytes?new Error("FILE_TOO_LARGE"):null,chunk);}}),createWriteStream(path,{flags:"wx"}));
  requireCondition(count>0,422,"EMPTY_FILE");
  if(extension==="txt") {const decoder=new TextDecoder("utf-8",{fatal:true});for await(const chunk of createReadStream(path)){requireCondition(!chunk.includes(0),415,"INVALID_TEXT");decoder.decode(chunk,{stream:true});}decoder.decode();return;}
  const file=await open(path,"r");
  try{
   const read=async(position:number,length:number)=>{requireCondition(position>=0&&length>=0&&position+length<=count,415,"INVALID_DOCUMENT");const buffer=Buffer.alloc(length);const result=await file.read(buffer,0,length,position);requireCondition(result.bytesRead===length,415,"INVALID_DOCUMENT");return buffer;};
   if(extension==="pdf"){const head=await read(0,Math.min(count,8));const tail=await read(Math.max(0,count-2048),Math.min(count,2048));requireCondition(/^%PDF-1\.[0-7]|^%PDF-2\.0/.test(head.toString("ascii"))&&tail.includes(Buffer.from("%%EOF")),415,"INVALID_PDF");return;}
   const tail=await read(Math.max(0,count-65557),Math.min(count,65557));let eocd=-1;
   for(let i=tail.length-22;i>=0;i--)if(tail.readUInt32LE(i)===0x06054b50&&i+22+tail.readUInt16LE(i+20)===tail.length){eocd=i;break;}
   requireCondition(eocd>=0,415,"INVALID_DOCX");
   requireCondition(tail.readUInt16LE(eocd+4)===0&&tail.readUInt16LE(eocd+6)===0,415,"INVALID_DOCX");
   const entries=tail.readUInt16LE(eocd+10),length=tail.readUInt32LE(eocd+12),offset=tail.readUInt32LE(eocd+16);
   requireCondition(entries>0&&entries<=4096&&length<=1024*1024&&offset+length<=count-22,415,"INVALID_DOCX");
   const central=await read(offset,length);let cursor=0,uncompressed=0;const names=new Set<string>();const xml=new Map<string,string>();
   for(let i=0;i<entries;i++){
    requireCondition(cursor+46<=central.length&&central.readUInt32LE(cursor)===0x02014b50,415,"INVALID_DOCX");
    const flags=central.readUInt16LE(cursor+8),method=central.readUInt16LE(cursor+10),compressed=central.readUInt32LE(cursor+20),size=central.readUInt32LE(cursor+24),n=central.readUInt16LE(cursor+28),extra=central.readUInt16LE(cursor+30),comment=central.readUInt16LE(cursor+32),localOffset=central.readUInt32LE(cursor+42);
    requireCondition(cursor+46+n+extra+comment<=central.length,415,"INVALID_DOCX");const entry=central.subarray(cursor+46,cursor+46+n).toString("utf8");
    requireCondition(!(flags&1)&&[0,8].includes(method)&&!entry.startsWith("/")&&!entry.includes("\\")&&!entry.split("/").includes("..")&&!names.has(entry),415,"INVALID_DOCX");
    requireCondition(!/vbaProject\.bin$/i.test(entry),415,"MACROS_NOT_ALLOWED");names.add(entry);uncompressed+=size;requireCondition(uncompressed<=100*1024*1024,415,"DOCX_EXPANSION_LIMIT");
    const local=await read(localOffset,30);requireCondition(local.readUInt32LE(0)===0x04034b50,415,"INVALID_DOCX");const nameLength=local.readUInt16LE(26);const dataOffset=localOffset+30+nameLength+local.readUInt16LE(28);
    requireCondition(dataOffset+compressed<=offset,415,"INVALID_DOCX");requireCondition((await read(localOffset+30,nameLength)).toString("utf8")===entry,415,"INVALID_DOCX");
    if(["[Content_Types].xml","word/document.xml","_rels/.rels"].includes(entry)){
     requireCondition(compressed<=1024*1024&&size<=1024*1024,415,"DOCX_XML_LIMIT");const bytes=await read(dataOffset,compressed);const inflated=method===0?bytes:inflateRawSync(bytes,{maxOutputLength:1024*1024});requireCondition(inflated.length===size,415,"INVALID_DOCX");const value=new TextDecoder("utf-8",{fatal:true}).decode(inflated);requireCondition(!/<!DOCTYPE|<!ENTITY/i.test(value),415,"INVALID_DOCX");xml.set(entry,value);
    }
    cursor+=46+n+extra+comment;
   }
   requireCondition(cursor===central.length&&xml.get("[Content_Types].xml")?.includes("wordprocessingml.document.main+xml")&&xml.get("word/document.xml")?.includes("document")&&xml.get("_rels/.rels")?.includes("officeDocument"),415,"INVALID_DOCX");
  }finally{await file.close();}
 }finally{await rm(root,{recursive:true,force:true});}
}
