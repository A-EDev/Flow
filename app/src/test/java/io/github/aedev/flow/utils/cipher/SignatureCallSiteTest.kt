package io.github.aedev.flow.utils.cipher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The signature call site in the shape player `5203c085` uses, with its identifiers renamed. */
class SignatureCallSiteTest {
    @Test
    fun `the decipher call and its preprocessing are read from the call site`() {
        val js =
            """var h=new g.bo(H.url,!0);if(H.s){var B=h,k=H.sp,T=qz(7,1234,wk(31,987,H.s));B[aX[18]](k,qz(52,6000,T))}"""

        val info = FunctionNameExtractor.extractSigFunctionInfo(js)

        assertThat(info).isNotNull()
        assertThat(info!!.name).isEqualTo("qz")
        assertThat(info.constantArgs).containsExactly(7, 1234).inOrder()
        assertThat(info.preprocessFunc).isEqualTo("wk")
        assertThat(info.preprocessArgs).containsExactly(31, 987).inOrder()
    }

    @Test
    fun `a player without that shape yields no call site`() {
        val js = """var a=function(b){b=b.split("");b.reverse();return b.join("")};"""

        assertThat(FunctionNameExtractor.extractSigFunctionInfo(js)).isNull()
    }
}
