package io.outblock.lilico.page.transaction.record

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import io.outblock.lilico.R
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.databinding.ActivityTransactionRecordBinding
import io.outblock.lilico.page.transaction.record.presenter.TransactionRecordPresenter
import io.outblock.lilico.utils.extensions.res2String
import io.outblock.lilico.utils.extensions.res2color

class TransactionRecordActivity : BaseActivity() {
    private val contractId by lazy { intent.getStringExtra(EXTRA_CONTRACT_ID) }

    private lateinit var binding: ActivityTransactionRecordBinding

    private lateinit var presenter: TransactionRecordPresenter
    private lateinit var viewModel: TransactionRecordViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        presenter = TransactionRecordPresenter(binding, this)
        viewModel = ViewModelProvider(this)[TransactionRecordViewModel::class.java].apply {
            setContractId(contractId)
            transferCountLiveData.observe(this@TransactionRecordActivity) { presenter.bind(it ?: 0) }
            transferListLiveData.observe(this@TransactionRecordActivity) {
                presenter.setListData(it)
            }
            load()
        }

        binding.refreshLayout.setOnRefreshListener { viewModel.load() }

        setupToolbar()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupToolbar() {
        binding.toolbar.navigationIcon?.mutate()?.setTint(R.color.neutrals1.res2color())
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = R.string.transactions.res2String()
    }

    companion object {
        private const val EXTRA_CONTRACT_ID = "contract_id"

        fun launch(context: Context, contractId: String? = null) {
            context.startActivity(Intent(context, TransactionRecordActivity::class.java).apply {
                putExtra(EXTRA_CONTRACT_ID, contractId)
            })
        }
    }
}