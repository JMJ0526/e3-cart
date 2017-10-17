package org.jmj.controller;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.jmj.bean.TbItem;
import org.jmj.bean.TbUser;
import org.jmj.cart.CartService;
import org.jmj.easy.ResponseResult;
import org.jmj.service.ItemSerivce;
import org.jmj.util.JsonUtils;
import org.jmj.utils.CookieUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class CartController {

	@Autowired
	private ItemSerivce itemSerivce;

	@Autowired
	private CartService cartService;

	/**
	 * 添加商品到购物车
	 * @param itemid
	 * @param num
	 * @param model
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping("/cart/add/{itemid}.html")
	public String addCartNoneLogin(@PathVariable("itemid") Long itemid, Integer num, Model model,
			HttpServletRequest request, HttpServletResponse response) {

		// 从request中获取user
		TbUser user = (TbUser) request.getAttribute("user");
		
		// 商品列表
		List<TbItem> list = null;
		// 存放在redis中的购物车
		String redisCart = null;
		// 如果处于已登录状态
		if (user != null) {
			Long id = user.getId();
			redisCart = cartService.getCartByRedis("userCart_"+id, "cart");
			// redis中的购车不为空
			if (!redisCart.equals("null")) {
				list = JsonUtils.jsonToList(redisCart, TbItem.class);
				// 检查购物车中是否已经添加该物品
				boolean b = checkExistedItem(itemid, list, num);
				addItemToCart(itemid, num, list, b);

				String result = JsonUtils.objectToJson(list);
				cartService.setCartToRedis("userCart_"+id, "cart", result);
				CookieUtils.setCookie(request, response, "cart", result, 3600, "UTF-8");
				model.addAttribute("cartList", list);
				return "cart";
			}
		}

		String cookieCart = CookieUtils.getCookieValue(request, "cart", "UTF-8");
		System.out.println("cookieCart:"+cookieCart);
		// 如果用户处于未登录状态
		if (list == null) {
			// 从cookie中取出本地的购物车列表
			if (StringUtils.isNotEmpty(cookieCart)) { // 本地购物车不为空
				list = JsonUtils.jsonToList(cookieCart, TbItem.class);
				boolean b = checkExistedItem(itemid, list, num);
				addItemToCart(itemid, num, list, b);
			} else {
				list = new ArrayList<>();
				addItemToCart(itemid, num, list, false);
			}
		}
		String json = JsonUtils.objectToJson(list);
		CookieUtils.deleteCookie(request, response, "cart");
		CookieUtils.setCookie(request, response, "cart", json, 3600, "UTF-8");
		model.addAttribute("cartList", list);
		return "cart";
	}

	/**
	 * flag : 只有当checkExistedItem方法返回false时，才查询数据库 添加到购物车中
	 * @param itemid
	 * @param num
	 * @param list
	 * @param flag
	 */
	private void addItemToCart(Long itemid, Integer num, List<TbItem> list, boolean flag) {
		if (!flag) {
			TbItem tbItem = itemSerivce.getItemById(itemid);
			tbItem.setNum(num);
			tbItem.setImage(tbItem.getImage().split(",")[0]);
			list.add(tbItem);
		}
	}

	/**
	 * 检查购物车是否存在相同的商品，是返回true,否返回false
	 * @param itemid
	 * @param list
	 * @param num
	 * @return
	 */
	private boolean checkExistedItem(Long itemid, List<TbItem> list, int num) {
		boolean b = false;
		Iterator<TbItem> iterator = list.iterator();
		while (iterator.hasNext()) {
			TbItem tbItem = iterator.next();
			// 如果已经更改数量
			if (tbItem.getId().equals(itemid)) {
				tbItem.setNum(tbItem.getNum() + num);
				return true;
			}
		}
		return b;
	}
	
	/**
	 * 更新购物车
	 * @param itemId
	 * @param num
	 * @param request
	 * @return
	 */
	@ResponseBody
	@RequestMapping("/cart/add/cart/update/num/{itemId}/{num}.action")
	public ResponseResult updateCart(@PathVariable("itemId")Long itemId
			,@PathVariable("num")int num,HttpServletRequest request
			,HttpServletResponse response) {
		
		String string = CookieUtils.getCookieValue(request, "cart", "UTF-8");
		
		List<TbItem> list = JsonUtils.jsonToList(string, TbItem.class);
		for (TbItem tbItem : list) {
			if(tbItem.getId().equals(itemId)) {
				tbItem.setNum(num);
			}
		}
		
		String cart = JsonUtils.objectToJson(list);
		
		CookieUtils.setCookie(request,response,"cart",cart,3600,"UTF-8");
		
		TbUser user = (TbUser) request.getAttribute("user");
		if(user != null ) {
			cartService.setCartToRedis("userCart_"+user.getId(), "cart", cart);
		}
		return ResponseResult.ok();
	}
	
	/**
	 * 从购物车中删除商品
	 * @param itemId
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping("/cart/delete/{itemId}")
	public String deleteCartByItemId(@PathVariable("itemId")Long itemId
			,HttpServletRequest request,HttpServletResponse response) {
		
		TbUser user = (TbUser) request.getAttribute("user");
		
		String cookieValue = CookieUtils.getCookieValue(request, "cart", "UTF-8");
		
		List<TbItem> list = JsonUtils.jsonToList(cookieValue, TbItem.class);
		
		removeItemById(itemId,list);
		
		String cart = JsonUtils.objectToJson(list);
		
		if(user != null) {
			cartService.setCartToRedis("userCart_"+user.getId(), "cart", cart);
		}
		
		CookieUtils.deleteCookie(request, response, "cart");
		CookieUtils.setCookie(request,response,"cart",cart,3600,"UTF-8");
		request.setAttribute("cartList", list);
		System.out.println("size:"+list.size());
		
		return "cart";
	}
	
	private void removeItemById(Long id,List<TbItem> list) {
		Iterator<TbItem> iterator = list.iterator();
		flag:while (iterator.hasNext()) {
			TbItem tbItem = iterator.next();
			if(tbItem.getId().equals(id)) {
				iterator.remove();
				break flag;
			}
		}
	}
	
}
