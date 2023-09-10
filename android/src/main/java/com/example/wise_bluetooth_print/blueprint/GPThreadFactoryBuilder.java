package com.example.wise_bluetooth_print.blueprint;

import androidx.annotation.NonNull;

import java.util.concurrent.ThreadFactory;

/**
 * Created by Administrator
 *
 * @author 猿史森林
 *         Date: 2017/11/2
 *         Class description:
 */
public class GPThreadFactoryBuilder implements ThreadFactory {

	private String name;
	private int counter;

	public GPThreadFactoryBuilder(String name) {
		this.name = name;
		counter = 1;
	}

	@Override
	public Thread newThread(@NonNull Runnable runnable) {
		Thread thread = new Thread(runnable, name);
		thread.setName("ThreadFactoryBuilder_" + name + "_" + counter);
		return thread;
	}
}
