package com.quickbill.app

import android.app.Application
import com.quickbill.app.data.QuickBillDatabase
import com.quickbill.app.data.QuickBillRepository

class QuickBillApplication : Application() {
    val repository by lazy { QuickBillRepository(QuickBillDatabase.create(this)) }
}
