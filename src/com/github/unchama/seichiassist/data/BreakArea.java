package com.github.unchama.seichiassist.data;


import java.util.ArrayList;
import java.util.List;

import com.github.unchama.seichiassist.ActiveSkill;

public class BreakArea {
	//スキルタイプ番号
	int type;
	//スキルレベル
	int level;
	//南向きを基準として破壊の範囲座標
	Coordinate breaklength;
	//破壊回数
	int breaknum;
	//向いている方角
	String dir;
	//破壊範囲を示す相対座標リスト
	List<Coordinate> startlist,endlist;
	//変数として利用する相対座標
	private Coordinate start,end;

	public BreakArea(int type, int skilllevel) {
		this.type = type;
		this.level = skilllevel;
		this.dir = "S";
		this.startlist = new ArrayList<Coordinate>();
		this.endlist = new ArrayList<Coordinate>();
		//初期化
		ActiveSkill[] as = ActiveSkill.values();
		this.breaklength = as[type-1].getBreakLength(level);
		this.breaknum = as[type-1].getRepeatTimes(level);
	}
	public List<Coordinate> getStartList() {
		return startlist;
	}
	public List<Coordinate> getEndList() {
		return endlist;
	}
	public String getDir() {
		return dir;
	}
	public void setDir(String dir) {
		this.dir = dir;
	}
	//破壊範囲の設定
	public void makeArea(boolean assaultflag) {
		//種類が選択されていなければ終了
		if(type == 0){
			return;
		}
		startlist.clear();
		endlist.clear();
		//中心座標(0,0,0)のスタートとエンドを仮取得
		start = new Coordinate(-(breaklength.x-1)/2, -(breaklength.y-1)/2, -(breaklength.z-1)/2);
		end = new Coordinate((breaklength.x-1)/2, (breaklength.y-1)/2, (breaklength.z-1)/2);
		//アサルトスキルの時
		if(assaultflag){
			shift(0, (breaklength.y-1)/2 - 1,0);
		}
		//上向きまたは下向きの時
		else if(dir.equals("U") || dir.equals("D")){
			shift(0, (breaklength.y-1)/2, 0);
		}
		//それ以外の範囲
		else{
			shift(0, (breaklength.y-1)/2 - 1, (breaklength.z-1)/2);

		}
		//スタートリストに追加
		startlist.add(new Coordinate(start));
		endlist.add(new Coordinate(end));

		//破壊回数だけリストに追加

		for(int count = 1; count < breaknum;count++){
			switch(dir){
			case "N":
			case "E":
			case "S":
			case "W":
				shift(0, 0, breaklength.z);
				break;
			case "U":
			case "D":
				shift(0, breaklength.y, 0);
				break;

			}
			startlist.add(new Coordinate(start));
			endlist.add(new Coordinate(end));
		}


		switch(dir){
		case "N":
			rotateXZ(180);
			break;
		case "E":
			rotateXZ(270);
			break;
		case "S":
			break;
		case "W":
			rotateXZ(90);
			break;
		case "U":
			break;
		case "D":
			if(!assaultflag)multiply_Y(-1);
			break;
		}
	}
	private void multiply_Y(int i) {
		for(int count = 0;count < breaknum ; count++){
			Coordinate start = startlist.get(count);
			Coordinate end = endlist.get(count);
			Coordinate tmpstart = new Coordinate(startlist.get(count));
			if(i >=0){
				start.setXYZ(start.x,start.y * i,start.z);
				end.setXYZ(end.x,end.y * i,end.z);
			}else{
				start.setXYZ(start.x,end.y * i,start.z);
				end.setXYZ(end.x,tmpstart.y * i,end.z);
			}
		}
	}
	private void shift(int x, int y, int z) {
		start.add(x,y,z);
		end.add(x,y,z);
	}
	private void rotateXZ(int d) {
		for(int count = 0;count < breaknum ; count++){
			Coordinate start = startlist.get(count);
			Coordinate end = endlist.get(count);
			Coordinate tmpstart = new Coordinate(startlist.get(count));
			switch(d){
			case 90:
				start.setXYZ(-end.z, start.y, start.x);
				end.setXYZ(-tmpstart.z,end.y,end.x);
				break;
			case 180:
				start.setZ(-end.z);
				end.setZ(-tmpstart.z);
				break;
			case 270:
				start.setXYZ(start.z,start.y,start.x);
				end.setXYZ(end.z, end.y, end.x);
				break;
			case 360:
				break;
			}
		}
	}
	public Coordinate getBreakLength() {
		return breaklength;
	}
	public int getBreakNum() {
		return breaknum;
	}


}
