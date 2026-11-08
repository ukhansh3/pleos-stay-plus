package com.pleosstay.app.entertainment.karaoke

import kotlin.math.*

enum class PitchQuality { VOICED, SILENCE, TOO_QUIET, NOISY, CLIPPED, TOO_SHORT, INVALID }
data class PitchEstimate(val hz:Double?,val confidence:Double,val quality:PitchQuality)
/** YIN difference + cumulative mean normalization, threshold trough and parabolic refinement.
 * Input is mono normalized PCM. No gain normalization before silence/clipping classification.
 * Initial thresholds are explicit and need vehicle calibration; this is not a singer detector.
 */
class YinPitchDetector(val minHz:Double=65.0,val maxHz:Double=1_100.0,
    val threshold:Double=0.15,val minimumRms:Double=0.005) {
    init { require(minHz>0 && maxHz>minHz && threshold in 0.01..0.4 && minimumRms>0) }
    fun analyze(samples:FloatArray,sampleRate:Int):PitchEstimate {
        if(sampleRate !in 8_000..192_000 || samples.any{!it.isFinite() || abs(it)>1.001f}) return PitchEstimate(null,0.0,PitchQuality.INVALID)
        val maxLag=minOf((sampleRate/minHz).toInt(),samples.size/2-1)
        val minLag=maxOf(2,(sampleRate/maxHz).toInt())
        if(samples.size < 2*(sampleRate/minHz).toInt()+2 || maxLag<=minLag) return PitchEstimate(null,0.0,PitchQuality.TOO_SHORT)
        val rms=sqrt(samples.sumOf{it.toDouble()*it}/samples.size)
        if(rms<1e-7)return PitchEstimate(null,0.0,PitchQuality.SILENCE)
        if(rms<minimumRms)return PitchEstimate(null,0.0,PitchQuality.TOO_QUIET)
        if(samples.count{abs(it)>=0.995f}.toDouble()/samples.size>0.01)return PitchEstimate(null,0.0,PitchQuality.CLIPPED)
        val mean=samples.sumOf{it.toDouble()}/samples.size
        val normalized=DoubleArray(samples.size){(samples[it]-mean)/rms}
        val width=samples.size-maxLag
        val difference=DoubleArray(maxLag+1)
        for(tau in 1..maxLag) {
            var sum=0.0
            for(i in 0 until width){val delta=normalized[i]-normalized[i+tau];sum+=delta*delta}
            difference[tau]=sum
        }
        val cmnd=DoubleArray(maxLag+1){1.0};var cumulative=0.0
        for(tau in 1..maxLag){cumulative+=difference[tau];cmnd[tau]=if(cumulative>1e-12)difference[tau]*tau/cumulative else 1.0}
        var lag=minLag
        while(lag<=maxLag && cmnd[lag]>=threshold)lag++
        if(lag>maxLag)return PitchEstimate(null,0.0,PitchQuality.NOISY)
        while(lag<maxLag && cmnd[lag+1]<cmnd[lag])lag++
        val refined=if(lag>1 && lag<maxLag){
            val denominator=cmnd[lag-1]-2*cmnd[lag]+cmnd[lag+1]
            lag+if(abs(denominator)>1e-12)0.5*(cmnd[lag-1]-cmnd[lag+1])/denominator else 0.0
        }else lag.toDouble()
        val hz=sampleRate/refined
        if(!hz.isFinite() || hz<minHz || hz>maxHz)return PitchEstimate(null,0.0,PitchQuality.NOISY)
        return PitchEstimate(hz,(1-cmnd[lag]).coerceIn(0.0,1.0),PitchQuality.VOICED)
    }
    companion object {
        fun cents(inputHz:Double,referenceHz:Double):Double {
            require(inputHz.isFinite() && referenceHz.isFinite() && inputHz>0 && referenceHz>0)
            return 1200*ln(inputHz/referenceHz)/ln(2.0)
        }
    }
}
