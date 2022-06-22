package com.oldenweb;

// interface listener to send data from libGDX to native environment
public interface InterfaceListener {
	void saveScore(int score);
	void signIn();
	void signOut();
	void showLeaders();
	void admobInterstitial();
}
