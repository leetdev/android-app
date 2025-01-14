package com.kyberswap.android.presentation.main.balance

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.util.Attributes
import com.google.firebase.analytics.FirebaseAnalytics
import com.jakewharton.rxbinding3.widget.textChanges
import com.kyberswap.android.AppExecutors
import com.kyberswap.android.R
import com.kyberswap.android.data.repository.datasource.storage.StorageMediator
import com.kyberswap.android.databinding.FragmentBalanceBinding
import com.kyberswap.android.databinding.LayoutBalanceTargetBinding
import com.kyberswap.android.databinding.LayoutBalanceTargetBuyEthBinding
import com.kyberswap.android.databinding.LayoutSwipeTargetBinding
import com.kyberswap.android.databinding.LayoutTokenBalanceTargetBinding
import com.kyberswap.android.databinding.LayoutTokenPriceTargetBinding
import com.kyberswap.android.domain.SchedulerProvider
import com.kyberswap.android.domain.model.Token
import com.kyberswap.android.domain.model.Wallet
import com.kyberswap.android.presentation.base.BaseFragment
import com.kyberswap.android.presentation.common.CustomLinearLayoutManager
import com.kyberswap.android.presentation.common.PendingTransactionNotification
import com.kyberswap.android.presentation.common.TutorialView
import com.kyberswap.android.presentation.helper.Navigator
import com.kyberswap.android.presentation.main.MainActivity
import com.kyberswap.android.presentation.main.swap.SaveSendState
import com.kyberswap.android.presentation.main.swap.SaveSwapDataState
import com.kyberswap.android.presentation.splash.GetWalletState
import com.kyberswap.android.util.BAL
import com.kyberswap.android.util.BALANCE_ADDRESS_COPIED
import com.kyberswap.android.util.BALANCE_ADDRESS_SHOWN
import com.kyberswap.android.util.BALANCE_BUYETH_YES
import com.kyberswap.android.util.BALANCE_FAVOURITE_ADDED
import com.kyberswap.android.util.BALANCE_FAVOURITE_REMOVED
import com.kyberswap.android.util.BALANCE_NOTI_FLAG
import com.kyberswap.android.util.BALANCE_SEARCH_TOKEN
import com.kyberswap.android.util.BALANCE_SWIPELEFT_BUY
import com.kyberswap.android.util.BALANCE_SWIPELEFT_SELL
import com.kyberswap.android.util.BALANCE_SWIPELEFT_TRANSFER
import com.kyberswap.android.util.BALANCE_TOKEN_SORT
import com.kyberswap.android.util.BALANCE_TOKEN_TAPPED
import com.kyberswap.android.util.CHANGE_24H
import com.kyberswap.android.util.ETH
import com.kyberswap.android.util.FAVOURITE
import com.kyberswap.android.util.KYBER_LISTED
import com.kyberswap.android.util.LIST_TYPE
import com.kyberswap.android.util.NAME
import com.kyberswap.android.util.OTHERS
import com.kyberswap.android.util.TOKEN_NAME
import com.kyberswap.android.util.TOKEN_SORT
import com.kyberswap.android.util.USD
import com.kyberswap.android.util.di.ViewModelFactory
import com.kyberswap.android.util.ext.createEvent
import com.kyberswap.android.util.ext.exactAmount
import com.kyberswap.android.util.ext.formatDisplayNumber
import com.kyberswap.android.util.ext.openUrl
import com.kyberswap.android.util.ext.setTextIfChange
import com.kyberswap.android.util.ext.showDrawer
import com.kyberswap.android.util.ext.showKeyboard
import com.takusemba.spotlight.OnSpotlightListener
import com.takusemba.spotlight.OnTargetListener
import com.takusemba.spotlight.Spotlight
import com.takusemba.spotlight.Target
import com.takusemba.spotlight.shape.Circle
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_token_header.view.*
import java.math.BigDecimal
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BalanceFragment : BaseFragment(), PendingTransactionNotification, TutorialView {

    private lateinit var binding: FragmentBalanceBinding

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var appExecutors: AppExecutors

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var schedulerProvider: SchedulerProvider

    @Inject
    lateinit var analytics: FirebaseAnalytics

    private var currentSelectedView: View? = null

    private var isViewVisible: Boolean = false

    private val isCurrencySelected: Boolean
        get() = binding.header.tvEth == orderByOptions[orderBySelectedIndex] && binding.header.tvEth.compoundDrawables.isNotEmpty()
                || binding.header.tvUsd == orderByOptions[orderBySelectedIndex] && binding.header.tvUsd.compoundDrawables.isNotEmpty()

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(BalanceViewModel::class.java)
    }

    private val openedAddressView by lazy { listOf(binding.tvAddress, binding.tvQr) }

    private val handler by lazy {
        Handler()
    }

    private var wallet: Wallet? = null

    private val usd by lazy {
        getString(R.string.unit_usd)
    }

    private val eth by lazy {
        getString(R.string.unit_eth)
    }

    private val other by lazy {
        getString(R.string.other)
    }

    private val favourite by lazy {
        getString(R.string.favourite)
    }

    private val isOtherSelected: Boolean
        get() = currentSelectedView == binding.tvFavOther && isOther

    private val isFavoriteSelected: Boolean
        get() = currentSelectedView == binding.tvFavOther && !isOther

    private val isKyberListed: Boolean
        get() = currentSelectedView == binding.tvKyberList

    private val isOther: Boolean
        get() = binding.tvFavOther.text.toString().equals(other, true)

    private val nameAndBal by lazy {
        listOf(binding.header.tvName, binding.header.tvBalance)
    }

    private val balanceIndex by lazy {
        nameAndBal.indexOf(binding.header.tvBalance)
    }

    private var forceUpdate: Boolean = false

    private val orderByOptions by lazy {
        listOf(
            binding.header.tvName,
            binding.header.tvBalance,
            binding.header.tvEth,
            binding.header.tvUsd,
            binding.header.tvChange24h
        )
    }

    private var nameBalSelectedIndex: Int = 0

    private var orderBySelectedIndex: Int = 0

    private var currentSearchString = ""

    private var hasScrollToTop: Boolean = false

    private var tokenAdapter: TokenAdapter? = null

    private var spotlight: Spotlight? = null

    private val eventOrderParam: String
        get() = when {
            isKyberListed -> KYBER_LISTED
            isOther -> OTHERS
            else -> FAVOURITE
        }

    @Inject
    lateinit var mediator: StorageMediator

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentBalanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel.getSelectedWallet()
        tokenAdapter =
            TokenAdapter(appExecutors, handler,
                {
                    analytics.logEvent(
                        BALANCE_TOKEN_TAPPED,
                        Bundle().createEvent(TOKEN_NAME, it.tokenSymbol)
                    )
                    skipTutorial()
                    navigateToChartScreen(it)
                },
                {
                    if (wallet?.isPromo == true) {
                        moveToSwapTab()
                    } else {
                        wallet?.address?.let { it1 -> viewModel.save(it1, it, false) }
                    }
                    analytics.logEvent(
                        BALANCE_SWIPELEFT_BUY,
                        Bundle().createEvent(TOKEN_NAME, it.tokenSymbol)
                    )
                },
                {
                    if (wallet?.isPromo == true) {
                        moveToSwapTab()
                    } else {
                        wallet?.address?.let { it1 -> viewModel.save(it1, it, true) }
                    }
                    analytics.logEvent(
                        BALANCE_SWIPELEFT_SELL,
                        Bundle().createEvent(TOKEN_NAME, it.tokenSymbol)
                    )
                },
                {
                    if (it.tokenSymbol == getString(R.string.promo_source_token)) {
                        showAlertWithoutIcon(message = getString(R.string.can_not_tranfer_token))
                    } else {
                        wallet?.address?.let { it1 -> viewModel.saveSendToken(it1, it) }
                    }

                    analytics.logEvent(
                        BALANCE_SWIPELEFT_TRANSFER,
                        Bundle().createEvent(TOKEN_NAME, it.tokenSymbol)
                    )

                },
                {
                    viewModel.saveFav(it)
                    analytics.logEvent(
                        if (it.fav) BALANCE_FAVOURITE_ADDED else BALANCE_FAVOURITE_REMOVED,
                        Bundle().createEvent(TOKEN_NAME, it.tokenSymbol)
                    )
                }
            )

        // Always get the latest price info
        refresh()
//        getTokenBalances()
        tokenAdapter?.mode = Attributes.Mode.Single
        binding.rvToken.adapter = tokenAdapter

        viewModel.getSelectedWalletCallback.observe(viewLifecycleOwner, Observer { event ->
            event?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is GetWalletState.Success -> {
                        if (state.wallet.address != wallet?.address) {
                            this.wallet = state.wallet
                            forceUpdate = true
                            binding.tvUnit.setTextIfChange(state.wallet.unit)
                            tokenAdapter?.showEth(wallet?.unit == eth)
                        }

                        if (state.wallet.display() != binding.walletAddress) {
                            binding.walletAddress = state.wallet.display()
                        }
                    }
                    is GetWalletState.ShowError -> {

                    }
                }
            }
        })

        viewModel.visibilityCallback.observe(viewLifecycleOwner, Observer {
            it?.peekContent()?.let { visibility ->
                displayWalletBalance(visibility)
            }
        })

        context?.let {
            binding.swipeLayout.setColorSchemeColors(
                ContextCompat.getColor(
                    it,
                    R.color.colorAccent
                )
            )
        }

        binding.imgFlag.setOnClickListener {
            skipTutorial()
            navigator.navigateToNotificationScreen(currentFragment)
            analytics.logEvent(
                BALANCE_NOTI_FLAG,
                Bundle().createEvent()
            )
        }

        binding.tvKyberList.isSelected = true
        currentSelectedView = binding.tvKyberList

        binding.tvKyberList.setOnClickListener {
            if (it.isSelected) return@setOnClickListener
            tokenAdapter?.setTokenType(TokenType.LISTED, getFilterTokenList(currentSearchString))
            skipTutorial()
            setSelectedOption(it)
            handleEmptyList()
        }

        binding.tvFavOther.setOnClickListener {
            toggleOtherFavDisplay(it as TextView)
            skipTutorial()
            handleEmptyList()
        }

        openedAddressView.forEach { view ->
            view.setOnClickListener {
                skipTutorial()
                navigator.navigateToBalanceAddressScreen(currentFragment)
                analytics.logEvent(
                    BALANCE_ADDRESS_SHOWN,
                    Bundle().createEvent()
                )
            }
        }

        binding.tvCopy.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, wallet?.address)
                type = MIME_TYPE_TEXT
            }
            startActivity(sendIntent)
            analytics.logEvent(
                BALANCE_ADDRESS_COPIED,
                Bundle().createEvent()
            )
        }

        binding.imgVisibility.setOnClickListener {
            val selected = !it.isSelected
            it.isSelected = selected
            viewModel.updateVisibility(selected)
        }

        binding.imgMenu.setOnClickListener {
            showDrawer(true)
        }

        listOf(binding.edtSearch, binding.imgSearch).forEach {
            it.setOnClickListener {
                binding.edtSearch.requestFocus()
                it.showKeyboard()
                analytics.logEvent(
                    BALANCE_SEARCH_TOKEN,
                    Bundle().createEvent()
                )
            }

        }

        viewModel.compositeDisposable.add(
            binding.edtSearch.textChanges()
                .skipInitialValue().debounce(
                    250,
                    TimeUnit.MILLISECONDS
                )
                .map {
                    return@map it.trim().toString().toLowerCase(Locale.getDefault())
                }.observeOn(schedulerProvider.ui())
                .subscribe { searchedText ->
                    tokenAdapter?.let {
                        it.submitFilterList(getFilterTokenList(searchedText, it.getFullTokenList()))
                    }
                    handleEmptyList()
                    currentSearchString = searchedText
                })

        binding.edtSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.edtSearch.clearFocus()
            }
            false
        }

        binding.rvToken.layoutManager = CustomLinearLayoutManager(
            activity,
            RecyclerView.VERTICAL,
            false
        )


        viewModel.getBalanceStateCallback.observe(viewLifecycleOwner, Observer { event ->
            event?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is GetBalanceState.Success -> {
//                        if (binding.swipeLayout.isRefreshing) {
//                            binding.swipeLayout.isRefreshing = false
//                        }
                        updateTokenList(state.tokens.map {
                            it.updateSelectedWallet(wallet)
                        })
                    }
                    is GetBalanceState.ShowError -> {
//                        binding.swipeLayout.isRefreshing = false
                    }
                }
            }
        })

        viewModel.refreshBalanceStateCallback.observe(viewLifecycleOwner, Observer { event ->
            event?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is GetBalanceState.Success -> {
                        updateTokenList(state.tokens.map {
                            it.updateSelectedWallet(wallet)
                        })
                        if (state.isCompleted) {
                            if (binding.swipeLayout.isRefreshing) {
                                binding.swipeLayout.isRefreshing = false
                            }
                            getTokenBalances()
                            if (!hasScrollToTop) {
                                scrollToTop()
                                hasScrollToTop = true
                            }
                        }
                    }
                    is GetBalanceState.ShowError -> {
                        binding.swipeLayout.isRefreshing = false
                        getTokenBalances()
                    }
                }
            }
        })

        viewModel.saveTokenCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                when (state) {
                    is SaveTokenState.Success -> {
                        if (state.fav) {
                            showAlertWithoutIcon(message = getString(R.string.add_fav_success))
                        } else {
                            showAlertWithoutIcon(message = getString(R.string.remove_fav_success))
                        }
                    }
                    is SaveTokenState.ShowError -> {

                    }
                }
            }
        })


        binding.header.tvEth.setOnClickListener { view ->
            tokenAdapter?.let {
                orderByCurrency(
                    true,
                    it.toggleEth(),
                    view as TextView
                )
                analytics.logEvent(
                    BALANCE_TOKEN_SORT,
                    Bundle().createEvent(
                        listOf(LIST_TYPE, TOKEN_SORT),
                        listOf(eventOrderParam, ETH)
                    )
                )
            }
        }

        binding.header.tvUsd.setOnClickListener { view ->
            tokenAdapter?.let {
                orderByCurrency(
                    false,
                    it.toggleUsd(),
                    view as TextView
                )
                analytics.logEvent(
                    BALANCE_TOKEN_SORT,
                    Bundle().createEvent(
                        listOf(LIST_TYPE, TOKEN_SORT),
                        listOf(eventOrderParam, USD)
                    )
                )
            }
        }

        binding.header.tvChange24h.setOnClickListener { view ->
            tokenAdapter?.let {
                orderByChange24h(it.toggleChange24h(), view as TextView)

                analytics.logEvent(
                    BALANCE_TOKEN_SORT,
                    Bundle().createEvent(
                        listOf(LIST_TYPE, TOKEN_SORT),
                        listOf(eventOrderParam, CHANGE_24H)
                    )
                )
            }

        }

//        handler.postDelayed({
//            setNameBalanceSelectedOption(balanceIndex)
//        }, 250)

        nameAndBal.forEachIndexed { index, view ->
            view.setOnClickListener {
                setNameBalanceSelectedOption(getNameBalNextSelectedIndex(index), true)
            }
        }

        viewModel.visibilityCallback.observe(viewLifecycleOwner, Observer {
            it?.peekContent()?.let { visibility ->
                tokenAdapter?.hideBalance(visibility)
            }
        })

        binding.swipeLayout.setOnRefreshListener {
            refresh()
        }

        viewModel.saveWalletCallback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                showProgress(state == SaveWalletState.Loading)
                when (state) {
                    is SaveWalletState.Success -> {
                    }
                    is SaveWalletState.ShowError -> {
                        showError(
                            state.message ?: getString(R.string.something_wrong)
                        )
                    }
                }
            }
        })

        viewModel.callback.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                showProgress(state == SaveSwapDataState.Loading)
                when (state) {
                    is SaveSwapDataState.Success -> {
                        moveToSwapTab()
                    }
                    is SaveSwapDataState.ShowError -> {
                        showError(
                            state.message ?: getString(R.string.something_wrong)
                        )
                    }
                }
            }
        })

        viewModel.callbackSaveSend.observe(viewLifecycleOwner, Observer {
            it?.getContentIfNotHandled()?.let { state ->
                showProgress(state == SaveSendState.Loading)
                when (state) {
                    is SaveSendState.Success -> {
                        navigateToSendScreen()
                    }
                    is SaveSendState.ShowError -> {
                        showError(
                            state.message ?: getString(R.string.something_wrong)
                        )
                    }
                }
            }
        })

        binding.tvBuyEth.setOnClickListener {
            (activity as MainActivity).dialogHelper.showConfirmOpenBuyEth(
                {
                    openUrl(getString(R.string.buy_eth_endpoint))
                    analytics.logEvent(BALANCE_BUYETH_YES, Bundle().createEvent())
                }
            )

        }

        binding.root.doOnPreDraw {
            binding.rvToken.addOnChildAttachStateChangeListener(object :
                OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    if (binding.rvToken.childCount == 5) {
                        binding.rvToken.removeOnChildAttachStateChangeListener(this)
                        isViewVisible = true
                        showTutorial()
                    }
                }

                override fun onChildViewDetachedFromWindow(view: View) {}
            })
        }
    }

    fun getSelectedWallet() {
        viewModel.getSelectedWallet()
    }

    fun displaySpotLight() {
        if (activity == null) return
        val targets = ArrayList<Target>()
        val overlayTotalBalanceBinding =
            DataBindingUtil.inflate<LayoutBalanceTargetBinding>(
                LayoutInflater.from(activity), R.layout.layout_balance_target, null, false
            )

        val centreX = binding.textView3.x + binding.textView3.width / 1.5f
        val centreY = binding.tvBalance.y + binding.tvBalance.height / 2

        val firstTarget = Target.Builder()
            .setAnchor(centreX, centreY)
            .setShape(Circle(resources.getDimension(R.dimen.tutorial_75_dp)))
            .setOverlay(overlayTotalBalanceBinding.root)
            .setOnTargetListener(object : OnTargetListener {
                override fun onStarted() {
                }

                override fun onEnded() {
                    mediator.showBalanceTutorial(true)
                }
            })
            .build()
        targets.add(firstTarget)

        val overlayBuyEthBalanceBinding =
            DataBindingUtil.inflate<LayoutBalanceTargetBuyEthBinding>(
                LayoutInflater.from(activity),
                R.layout.layout_balance_target_buy_eth,
                null,
                false
            )

        val secondTarget = Target.Builder()
            .setAnchor(binding.tvBuyEth)
            .setShape(Circle(resources.getDimension(R.dimen.tutorial_75_dp)))
            .setOverlay(overlayBuyEthBalanceBinding.root)
            .setOnTargetListener(object : OnTargetListener {
                override fun onStarted() {
                }

                override fun onEnded() {
                }
            })
            .build()

        targets.add(secondTarget)
        val overlayTokenBalanceBinding =
            DataBindingUtil.inflate<LayoutTokenBalanceTargetBinding>(
                LayoutInflater.from(activity), R.layout.layout_token_balance_target, null, false
            )

        val location = IntArray(2)
        binding.header.tvName.getLocationInWindow(location)
        val x =
            location[0] + binding.header.tvName.width / 2f + resources.getDimension(R.dimen.tutorial_48_dp)
        val y =
            location[1] + binding.header.tvName.height / 2f + resources.getDimension(R.dimen.tutorial_96_dp)

        val thirdTarget = Target.Builder()
            .setAnchor(x, y)
            .setShape(Circle(resources.getDimension(R.dimen.tutorial_96_dp)))
            .setOverlay(overlayTokenBalanceBinding.root)
            .setOnTargetListener(object : OnTargetListener {
                override fun onStarted() {
                }

                override fun onEnded() {
                }
            })
            .build()

        targets.add(thirdTarget)

        val overlayTokenPriceTargetBinding =
            DataBindingUtil.inflate<LayoutTokenPriceTargetBinding>(
                LayoutInflater.from(activity), R.layout.layout_token_price_target, null, false
            )

        binding.header.view25.getLocationInWindow(location)
        val xUsd =
            location[0] + binding.header.view25.width / 2f + resources.getDimension(R.dimen.tutorial_36_dp)
        val yUsd =
            location[1] + binding.header.view25.height / 2f + resources.getDimension(R.dimen.tutorial_80_dp)

        val forthTarget = Target.Builder()
            .setAnchor(xUsd, yUsd)
            .setShape(Circle(resources.getDimension(R.dimen.tutorial_120_dp)))
            .setOverlay(overlayTokenPriceTargetBinding.root)
            .setOnTargetListener(object : OnTargetListener {
                override fun onStarted() {
                }

                override fun onEnded() {
                }
            })
            .build()

        targets.add(forthTarget)

        val overlaySwipeTargetBinding =
            DataBindingUtil.inflate<LayoutSwipeTargetBinding>(
                LayoutInflater.from(activity), R.layout.layout_swipe_target, null, false
            )

        val childView = binding.rvToken.findViewHolderForLayoutPosition(1)?.itemView
        childView?.let {
            childView.getLocationInWindow(location)
            val xSwipe =
                location[0] + childView.width * 3 / 4f
            val ySwipe =
                location[1] + childView.height / 2f
            val fifthTarget = Target.Builder()
                .setAnchor(xSwipe, ySwipe)
                .setShape(Circle(resources.getDimension(R.dimen.tutorial_120_dp)))
                .setOverlay(overlaySwipeTargetBinding.root)
                .setOnTargetListener(object : OnTargetListener {
                    override fun onStarted() {
                    }

                    override fun onEnded() {
                    }
                })
                .build()

            targets.add(fifthTarget)

        }

        // create spotlight
        spotlight = Spotlight.Builder(activity!!)
            .setBackgroundColor(R.color.color_tutorial)
            .setTargets(targets)
            .setDuration(1000L)
            .setAnimation(DecelerateInterpolator(2f))
            .setContainer(activity!!.window.decorView.findViewById(android.R.id.content))
            .setOnSpotlightListener(object : OnSpotlightListener {
                override fun onStarted() {
                }

                override fun onEnded() {
                }
            })
            .build()

        if (currentFragment is BalanceFragment) {
            spotlight?.start()
        } else {
            spotlight?.finish()
        }


        overlayTotalBalanceBinding.tvNext.setOnClickListener {
            spotlight?.next()
        }

        overlayBuyEthBalanceBinding.tvNext.setOnClickListener {
            spotlight?.next()
        }

        overlayTokenBalanceBinding.tvNext.setOnClickListener {
            spotlight?.next()
        }

        var swipeLayout: SwipeLayout? = null
        overlayTokenPriceTargetBinding.tvNext.setOnClickListener {
            spotlight?.next()
            swipeLayout =
                binding.rvToken.findViewHolderForLayoutPosition(1)?.itemView?.findViewById<SwipeLayout>(
                    R.id.swipe
                )
            swipeLayout?.open(true)
        }

        overlaySwipeTargetBinding.tvNext.setOnClickListener {
            spotlight?.next()
            swipeLayout?.close(true)
        }
    }

    fun showTutorial() {
        if (activity == null) return
        if (mediator.isShownBalanceTutorial()) return
        if (isViewVisible) {
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                displaySpotLight()
            }, 250)
        }
    }

    fun scrollToTop() {

        handler.postDelayed({
            binding.rvToken.smoothScrollToPosition(0)
        }, 250)
    }

    private fun updateTokenList(tokens: List<Token>) {
        tokenAdapter?.let {
            it.setFullTokenList(tokens)
            if (forceUpdate) {
                forceUpdate = false
                it.submitList(null)
            }
            it.submitFilterList(
                getFilterTokenList(
                    currentSearchString,
                    tokens
                )
            )

            setCurrencyDisplay(wallet?.unit == eth)
            displayWalletBalance(it.hideBalance)
            handleEmptyList()
        }
    }

    private fun getNameBalNextSelectedIndex(index: Int): Int {
        return if (index == nameBalSelectedIndex && tokenAdapter?.isNameBalOrder == true) {
            (nameBalSelectedIndex + 1) % 2
        } else {
            index
        }
    }

    private fun handleEmptyList() {
        handler.postDelayed({
            binding.tvEmpty.visibility =
                if (tokenAdapter?.itemCount == 0) View.VISIBLE else View.GONE
        }, 250)
    }

    private fun displayWalletBalance(isHide: Boolean) {
        binding.tvBalance.text =
            if (isHide) "******" else
                walletBalance.formatDisplayNumber().exactAmount()
    }

    override fun showPendingTxNotification(showNotification: Boolean) {
        if (::binding.isInitialized) {
            binding.vNotification.visibility = if (showNotification) View.VISIBLE else View.GONE
        }
    }

    override fun showUnReadNotification(showNotification: Boolean) {
        if (::binding.isInitialized) {
            binding.vFlagNotification.visibility = if (showNotification) View.VISIBLE else View.GONE
        }
    }

    private fun setSelectedOption(view: View) {
        if (view == currentSelectedView) return
        currentSelectedView?.isSelected = false
        view.isSelected = true
        currentSelectedView = view
    }

    private fun refresh() {
        viewModel.refresh()
        if (isOtherSelected && activity is MainActivity) {
            (activity as MainActivity).refreshOthers()
        }
    }

    private fun orderByCurrency(isEth: Boolean, type: OrderType, view: TextView) {
        if (view.isSelected || isCurrencySelected) {
            tokenAdapter?.let {
                it.setOrderBy(type, getFilterTokenList(currentSearchString))
                it.showEth(isEth)
                updateOrderDrawable(it.isAsc, view)
                displayWalletBalance(it.hideBalance)
            }
            updateWalletBalanceUnit(isEth)
            setCurrencyDisplay(isEth)
            updateOrderOption(orderByOptions.indexOf(view), view)
        } else {
            updateWalletBalanceUnit(isEth)
            setCurrencyDisplay(isEth)
            tokenAdapter?.let {
                it.showEth(isEth)
                displayWalletBalance(it.hideBalance)
            }
        }
    }

    private fun updateWalletBalanceUnit(isEth: Boolean) {
        val unit = if (isEth) eth else usd
        val updatedWallet = wallet?.copy(unit = unit)
        if (updatedWallet != wallet) {
            wallet = updatedWallet
            viewModel.updateWallet(updatedWallet)
        }

        wallet?.let {
            binding.tvUnit.setTextIfChange(it.unit)
        }
    }

    private val walletBalance: BigDecimal
        get() {
            return calcBalance(
                tokenAdapter?.getFullTokenList() ?: listOf(),
                tokenAdapter?.showEth == true
            )
        }

    private fun getTokenBalances() {
        viewModel.getTokenBalance()
    }

    private fun orderByChange24h(type: OrderType, view: TextView) {
        tokenAdapter?.let {
            it.setOrderBy(type, getFilterTokenList(currentSearchString))
            updateOrderDrawable(it.isAsc, view)
            analytics.logEvent(
                BALANCE_TOKEN_SORT,
                Bundle().createEvent(
                    listOf(LIST_TYPE, TOKEN_SORT),
                    listOf(eventOrderParam, CHANGE_24H)
                )
            )
        }
        updateOrderOption(orderByOptions.indexOf(view), view)
    }

    private fun updateOrderOption(index: Int, view: TextView) {
        if (index != orderBySelectedIndex) {
            toggleDisplay(false, orderByOptions[orderBySelectedIndex])
            orderBySelectedIndex = index
            toggleDisplay(true, view)
        }
    }

    private fun updateOrderDrawable(isAsc: Boolean, view: TextView) {
        if (isAsc) {
            view.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_upward, 0)
        } else {
            view.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_downward, 0)
        }
    }

    private fun navigateToSendScreen() {
        navigator.navigateToSendScreen((activity as MainActivity).getCurrentFragment(), wallet)
    }

    private fun navigateToChartScreen(token: Token?) {
        navigator.navigateToChartScreen(
            (activity as MainActivity).getCurrentFragment(),
            wallet,
            token,
            if (wallet?.unit == getString(R.string.unit_usd)) token?.symbol + "_USDC" else token?.symbol + "_ETH"
        )
    }

    private fun getFilterTokenList(searchedString: String, tokens: List<Token>): List<Token> {
        if (searchedString.isEmpty()) return tokens
        return tokens.filter { token ->
            token.tokenSymbol.toLowerCase(Locale.getDefault()).contains(searchedString, true) or
                    token.tokenName.toLowerCase(Locale.getDefault()).contains(searchedString, true)
        }
    }

    private fun getFilterTokenList(searchedString: String): List<Token> {
        val tokenList = tokenAdapter?.getFullTokenList() ?: listOf()
        return getFilterTokenList(searchedString, tokenList)
    }

    private fun calcBalance(tokens: List<Token>, isETH: Boolean): BigDecimal {
        var balance = BigDecimal.ZERO
        tokens.forEach { token ->
            balance +=
                if (token.currentBalance == BigDecimal.ZERO) {
                    BigDecimal.ZERO
                } else {
                    token.currentBalance.multiply(
                        if (isETH) {
                            token.rateEthNowOrDefaultValue
                        } else {
                            token.rateUsdNow
                        }
                    )
                }
        }
        return balance
    }

    override fun onDestroyView() {
        viewModel.compositeDisposable.clear()
        handler.removeCallbacksAndMessages(null)
        isViewVisible = false
        spotlight?.finish()
        super.onDestroyView()
    }

    override fun skipTutorial() {
        spotlight?.finish()
    }

    private fun moveToSwapTab() {
        if (activity is MainActivity) {
            handler.post {
                activity?.bottomNavigation?.currentItem = 1
            }
        }
    }

    private fun toggleDisplay(isSelected: Boolean, view: TextView) {

        if (view != binding.header.tvEth && view != binding.header.tvUsd) {
            view.isSelected = isSelected
        }

        val drawable = if (isSelected) R.drawable.ic_arrow_downward else 0
        view.setCompoundDrawablesWithIntrinsicBounds(0, 0, drawable, 0)
    }

    private fun setCurrencyDisplay(isEth: Boolean) {
        binding.header.tvEth.isSelected = isEth
        binding.header.tvUsd.isSelected = !isEth
    }

    private fun toggleOtherFavDisplay(view: TextView) {
        if (view.isSelected) {
            view.text =
                if (view.text == other) favourite else other
        }

        if (view.text == other) {
            tokenAdapter?.setTokenType(TokenType.OTHER, getFilterTokenList(currentSearchString))
        } else {
            tokenAdapter?.setTokenType(TokenType.FAVOURITE, getFilterTokenList(currentSearchString))
        }

        setSelectedOption(view)
    }

    private fun setNameBalanceSelectedOption(index: Int, forceUpdate: Boolean = false) {
        tokenAdapter?.let {
            toggleDisplay(false, nameAndBal[nameBalSelectedIndex])
            val selectedView = nameAndBal[index]
            toggleDisplay(true, selectedView)
            if (selectedView == binding.header.tvName) {
                it.setOrderBy(OrderType.NAME, getFilterTokenList(currentSearchString), forceUpdate)
                analytics.logEvent(
                    BALANCE_TOKEN_SORT,
                    Bundle().createEvent(
                        listOf(LIST_TYPE, TOKEN_SORT),
                        listOf(eventOrderParam, NAME)
                    )
                )
            } else if (selectedView == binding.header.tvBalance) {
                it.setOrderBy(
                    OrderType.BALANCE,
                    getFilterTokenList(currentSearchString),
                    forceUpdate
                )

                analytics.logEvent(
                    BALANCE_TOKEN_SORT,
                    Bundle().createEvent(
                        listOf(LIST_TYPE, TOKEN_SORT),
                        listOf(eventOrderParam, BAL)
                    )
                )
            }
            nameBalSelectedIndex = index
            updateOrderOption(orderByOptions.indexOf(selectedView), selectedView)
        }
    }

    companion object {
        private const val MIME_TYPE_TEXT = "text/plain"
        fun newInstance() = BalanceFragment()
    }
}
