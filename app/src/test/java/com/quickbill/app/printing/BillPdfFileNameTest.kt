package com.quickbill.app.printing

import org.junit.Assert.assertEquals
import org.junit.Test

class BillPdfFileNameTest {
    @Test
    fun usesTheBillNumberInTheRequestedPdfName() {
        assertEquals("QuickBill-1.pdf", billPdfFileName(1))
        assertEquals("QuickBill-2407.pdf", billPdfFileName(2407))
    }

    @Test
    fun preservesLargeBillNumbers() {
        assertEquals("QuickBill-9223372036854775807.pdf", billPdfFileName(Long.MAX_VALUE))
    }
}
