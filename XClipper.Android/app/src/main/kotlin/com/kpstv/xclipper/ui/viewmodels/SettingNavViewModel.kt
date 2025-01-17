package com.kpstv.xclipper.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.kpstv.navigation.*
import com.kpstv.xclipper.extensions.FragClazz
import com.kpstv.xclipper.ui.fragments.Settings

class SettingNavViewModel : ViewModel() {
    private val _navigation = MutableLiveData<NavigationOptions>(null)
    val navigation: LiveData<NavigationOptions> = _navigation

    fun navigateTo(
        screen: Settings.Screen,
        args: BaseArgs? = null,
        transactionType: FragmentNavigator.TransactionType = FragmentNavigator.TransactionType.REPLACE,
        animation: NavAnimation = AnimationDefinition.SlideInRight,
        addToBackStack: Boolean = true
    ) {
        _navigation.value = NavigationOptions(
            clazz = screen.clazz,
            navOptions = FragmentNavigator.NavOptions(
                args = args,
                animation = animation,
                transaction = transactionType,
                remember = addToBackStack
            )
        )
    }

    data class NavigationOptions(
        val clazz: FragClazz,
        val navOptions: FragmentNavigator.NavOptions
    )
}