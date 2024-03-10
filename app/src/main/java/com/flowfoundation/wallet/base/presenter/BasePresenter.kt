package com.flowfoundation.wallet.base.presenter

interface BasePresenter<T> {
    fun bind(model: T)
}
