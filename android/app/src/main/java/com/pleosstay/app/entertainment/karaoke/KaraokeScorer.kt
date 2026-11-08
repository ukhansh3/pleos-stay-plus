package com.pleosstay.app.entertainment.karaoke

import com.pleosstay.app.entertainment.data.*
import kotlin.math.*

data class PitchFrame(val startMillis:Long,val endMillis:Long,val estimate:PitchEstimate)
enum class KaraokeResultStatus { SCORED, INSUFFICIENT_INPUT, INPUT_ERROR, LEAKAGE_SUSPECTED }
data class KaraokeScore(val status:KaraokeResultStatus,val score:Int?,val coverage:Double,val voicedMillis:Long,
    val expectedMillis:Long,val message:String)
/** First calibrated policy: 25c full credit, linear falloff to 200c; 20%/500ms minimum input.
 * Missing sung intervals stay in the denominator. Overlapping/repeated frames cannot inflate score.
 */
data class ScoringPolicy(val perfectCents:Double=25.0,val zeroCents:Double=200.0,
    val minimumCoverage:Double=0.2,val minimumVoicedMillis:Long=500,val minimumConfidence:Double=0.85) {
    init { require(perfectCents>=0 && zeroCents>perfectCents && minimumCoverage in 0.0..1.0 && minimumVoicedMillis>0 && minimumConfidence in 0.0..1.0) }
}
class KaraokeScorer(private val policy:ScoringPolicy=ScoringPolicy()) {
    fun score(song:KaraokeSong,frames:List<PitchFrame>,inputLatencyMillis:Long=0,inputError:Boolean=false,leakageSuspected:Boolean=false):KaraokeScore {
        require(abs(inputLatencyMillis)<=5_000)
        val expected=song.melody.filter{it.midi!=null}.sumOf{it.endMillis-it.startMillis}
        if(inputError)return KaraokeScore(KaraokeResultStatus.INPUT_ERROR,null,0.0,0,expected,"마이크 입력에 실패했습니다.")
        if(leakageSuspected)return KaraokeScore(KaraokeResultStatus.LEAKAGE_SUSPECTED,null,0.0,0,expected,"반주 누설이 의심되어 채점하지 않았습니다.")
        var previousEnd=Long.MIN_VALUE;var voiced=0L;var credit=0.0
        frames.forEach { frame ->
            require(frame.startMillis>=0 && frame.endMillis<=song.durationMillis+5_000 && frame.endMillis>frame.startMillis && frame.startMillis>=previousEnd)
            previousEnd=frame.endMillis
            val estimate=frame.estimate
            if(estimate.quality!=PitchQuality.VOICED || estimate.hz==null || !estimate.hz.isFinite() || estimate.hz<=0 || !estimate.confidence.isFinite() || estimate.confidence !in policy.minimumConfidence..1.0)return@forEach
            val start=frame.startMillis-inputLatencyMillis;val end=frame.endMillis-inputLatencyMillis
            song.melody.filter{it.midi!=null}.forEach { note ->
                val overlap=(minOf(end,note.endMillis)-maxOf(start,note.startMillis)).coerceAtLeast(0)
                if(overlap>0){
                    val error=abs(YinPitchDetector.cents(estimate.hz,note.hz!!))
                    val accuracy=if(error<=policy.perfectCents)1.0 else ((policy.zeroCents-error)/(policy.zeroCents-policy.perfectCents)).coerceIn(0.0,1.0)
                    voiced+=overlap;credit+=overlap*accuracy
                }
            }
        }
        val coverage=voiced.toDouble()/expected
        if(voiced<policy.minimumVoicedMillis || coverage<policy.minimumCoverage)return KaraokeScore(KaraokeResultStatus.INSUFFICIENT_INPUT,null,coverage,voiced,expected,"음성을 충분히 감지하지 못했습니다.")
        return KaraokeScore(KaraokeResultStatus.SCORED,(100*credit/expected).roundToInt().coerceIn(0,100),coverage,voiced,expected,"음정과 입력 커버리지를 반영한 점수입니다.")
    }
}
data class KaraokeRecord(val sessionId:String,val songId:String,val title:String,val score:Int,val recordedAt:Long)
class KaraokeHistory(private val store:EntertainmentStore) {
    private data class History(val recent:List<KaraokeRecord>,val best:Map<String,Int>)
    private fun load():History {
        val bytes=store.read("karaoke-history")?:return History(emptyList(),emptyMap())
        return decodeRecord(bytes){
            require(readInt()==1);val count=readInt();require(count in 0..200)
            val recent=List(count){KaraokeRecord(readUTF(),readUTF(),readUTF(),readInt(),readLong()).also{require(it.score in 0..100 && it.sessionId.isNotBlank() && it.songId.isNotBlank())}}
            val bestCount=readInt();require(bestCount in 0..1000)
            val best=buildMap{repeat(bestCount){val id=readUTF();val score=readInt();require(id.isNotBlank() && score in 0..100 && !containsKey(id));put(id,score)}}
            require(recent.all{it.score<=(best[it.songId]?:-1)})
            History(recent,best)
        }
    }
    @Synchronized fun records():List<KaraokeRecord> = load().recent
    @Synchronized fun bestScores():Map<String,Int> = load().best
    @Synchronized fun save(record:KaraokeRecord) {
        require(record.score in 0..100 && record.sessionId.isNotBlank() && record.songId.isNotBlank())
        val previous=load();if(previous.recent.any{it.sessionId==record.sessionId})return
        val best=previous.best.toMutableMap();best[record.songId]=maxOf(best[record.songId]?:0,record.score)
        require(best.size<=1000)
        val next=(previous.recent+record).takeLast(200)
        // Recent results and lifetime best are committed together, never as separate writes.
        store.write("karaoke-history",encodeRecord{
            writeInt(1);writeInt(next.size);next.forEach{writeUTF(it.sessionId);writeUTF(it.songId);writeUTF(it.title);writeInt(it.score);writeLong(it.recordedAt)}
            writeInt(best.size);best.toSortedMap().forEach{(id,score)->writeUTF(id);writeInt(score)}
        })
    }
}
