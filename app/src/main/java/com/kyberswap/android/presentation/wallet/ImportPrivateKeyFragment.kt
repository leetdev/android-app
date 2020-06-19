package com.kyberswap.android.presentation.wallet

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.zxing.integration.android.IntentIntegrator
import com.kyberswap.android.AppExecutors
import com.kyberswap.android.R
import com.kyberswap.android.databinding.FragmentImportPrivateKeyBinding
import com.kyberswap.android.presentation.base.BaseFragment
import com.kyberswap.android.presentation.helper.Navigator
import com.kyberswap.android.presentation.landing.ImportWalletState
import com.kyberswap.android.presentation.listener.addTextChangeListener
import com.kyberswap.android.util.ERROR_TEXT
import com.kyberswap.android.util.WALLET_IMPORT_FAIL
import com.kyberswap.android.util.WALLET_IMPORT_SUCCESS
import com.kyberswap.android.util.WALLET_TYPE
import com.kyberswap.android.util.WALLET_TYPE_PRIVATE_KEY
import com.kyberswap.android.util.di.ViewModelFactory
import com.kyberswap.android.util.ext.createEvent
import org.consenlabs.tokencore.wallet.model.Messages
import javax.inject.Inject

class ImportPrivateKeyFragment : BaseFragment() {

    private lateinit var binding: FragmentImportPrivateKeyBinding

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var appExecutors: AppExecutors

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private var fromMain: Boolean = false

    @Inject
    lateinit var firebaseAnalytics: FirebaseAnalytics

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(ImportPrivateKeyViewModel::class.java)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fromMain = arguments!!.getBoolean(FROM_MAIN_PARAM)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentImportPrivateKeyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        binding.edtPrivateKey.addTextChangeListener {
            onTextChanged { s, _, _, _ ->
                val isValidPrivateKey = s.toString().trim().length == 64
                binding.btnImportWallet.isEnabled = isValidPrivateKey
                if (isValidPrivateKey) {
                    binding.textInputLayout.error = ""
                } else {
                    binding.textInputLayout.error = getString(R.string.private_key_length_condition)
                }
            }
        }

        binding.btnImportWallet.setOnClickListener {
            viewModel.importFromPrivateKey(
                binding.edtPrivateKey.text?.trim().toString(),
                if (binding.edtWalletName.text.isNotEmpty()) binding.edtWalletName.text.toString()
                else getString(R.string.default_wallet_name)
            )
        }

        viewModel.importWalletCallback.observe(viewLifecycleOwner, Observer {
            it?.let { state ->
                showProgress(state == ImportWalletState.Loading)
                when (state) {
                    is ImportWalletState.Success -> {
                        showAlert(getString(R.string.import_wallet_success)) {
                            if (fromMain) {
                                activity?.onBackPressed()
                            } else {
                                navigator.navigateToHome()
                            }
                        }
                        firebaseAnalytics.logEvent(
                            WALLET_IMPORT_SUCCESS, Bundle().createEvent(
                                WALLET_TYPE, WALLET_TYPE_PRIVATE_KEY
                            )
                        )
                    }
                    is ImportWalletState.ShowError -> {
                        val message = when (state.message) {
                            Messages.WALLET_EXISTS -> {
                                getString(R.string.wallet_exist)
                            }

                            Messages.PRIVATE_KEY_INVALID -> {
                                getString(R.string.fail_import_private_key)
                            }

                            Messages.MNEMONIC_BAD_WORD -> {
                                getString(R.string.fail_import_mnemonic)
                            }

                            Messages.MAC_UNMATCH -> {
                                getString(R.string.fail_import_json)
                            }
                            else -> {
                                state.message ?: getString(R.string.something_wrong)
                            }

                        }
                        firebaseAnalytics.logEvent(
                            WALLET_IMPORT_FAIL, Bundle().createEvent(
                                listOf(
                                    WALLET_TYPE, ERROR_TEXT
                                ), listOf(WALLET_TYPE_PRIVATE_KEY, message)
                            )
                        )

                        showAlertWithoutIcon(
                            message = message
                        )
                    }
                }
            }
        })

        binding.imgQR.setOnClickListener {
            IntentIntegrator.forSupportFragment(this)
                .setBeepEnabled(false)
                .initiateScan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                showAlertWithoutIcon(message = getString(R.string.message_cancelled))
            } else {
                binding.edtPrivateKey.setText(result.contents.toString())
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        private const val FROM_MAIN_PARAM = "from_main_param"
        fun newInstance(fromMain: Boolean) =
            ImportPrivateKeyFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(FROM_MAIN_PARAM, fromMain)
                }
            }
    }
}
